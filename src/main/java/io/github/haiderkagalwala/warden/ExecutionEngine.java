package io.github.haiderkagalwala.warden;

import io.github.haiderkagalwala.warden.stream.StreamConsumer;
import io.github.haiderkagalwala.warden.outcome.ProcessOutcome;
import io.github.haiderkagalwala.warden.utils.DrainUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ExecutionEngine {


    public static ProcessOutcome executeSyn(List<String> command, Path workingDir, Duration timeout,
                                            boolean timeoutEnabled, boolean captureStdout, boolean captureStderr,
                                            boolean mergeOutputAndError, StreamConsumer stdoutConsumer, StreamConsumer stderrConsumer) {


        var pb = new ProcessBuilder(command);
        if (workingDir != null) pb.directory(workingDir.toFile());
        if (mergeOutputAndError) pb.redirectErrorStream(true);
        if (stdoutConsumer == null && !captureStdout)
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        if (stderrConsumer == null && !captureStderr && !mergeOutputAndError)
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);

        // 2. Start process
        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            return new ProcessOutcome.Failed(e);
        }

        var startTime = Instant.now();
        var capturedStdout = new ByteArrayOutputStream();
        var capturedStderr = new ByteArrayOutputStream();

        // 3. Drain concurrently using virtual threads
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {

            var stdoutFuture = executor.submit(() -> {
                DrainUtils.drain(process.getInputStream(), capturedStdout, stdoutConsumer, captureStdout);
                return null;
            });

            var stderrFuture = mergeOutputAndError ? null :
                    executor.submit(() -> {
                        DrainUtils.drain(process.getErrorStream(), capturedStderr, stderrConsumer, captureStderr);
                    return null;
            });

            boolean finished;
            if (timeoutEnabled) {
                try {
                    finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    process.destroyForcibly();
                    return new ProcessOutcome.Failed(e);
                }
            } else {
                process.waitFor();
                finished = true;
            }

            stdoutFuture.get();
            if (stderrFuture != null) stderrFuture.get();


            var duration = Duration.between(startTime, Instant.now());
            var stdout = capturedStdout.toByteArray();
            var stderr = capturedStderr.toByteArray();

            // 6. Return correct outcome type
            if (!finished) {
                process.destroyForcibly(); // ← kill before returning
                return new ProcessOutcome.TimedOut(duration, stdout, stderr);
            }
            return new ProcessOutcome.Completed(
                    process.exitValue(),
                    stdout,
                    stderr,
                    duration
            );

        } catch (Exception e) {
            return new ProcessOutcome.Failed(e);
        }

    }


}

