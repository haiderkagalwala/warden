# Warden

A modern Java 21 process execution library built for the Virtual Threads era. Warden eliminates common subprocess pitfalls — 64 KB stream deadlocks, zombie processes, and blocked threads — and delivers native interactive terminal (PTY) support out of the box.

---

## Requirements

- Java 21+

---

## Installation

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.haiderkagalwala</groupId>
    <artifactId>warden</artifactId>
    <version>main-SNAPSHOT</version>
</dependency>
```

---

## Quick Start

```java
// Synchronous — blocks until the process exits
ProcessOutcome outcome = Warden.run("git", "status")
        .onStdout(ProcessStreams.printToStdout())
        .execute();

// Asynchronous — returns a handle immediately
PipeHandle handle = Warden.run("tail", "-f", "/var/log/app.log")
        .noTimeout()
        .onStdout(ProcessStreams.printToStdout())
        .executeAsync();
handle.cancel();

// Interactive PTY
PtyHandle shell = Warden.interactive("bash")
        .ptySize(220, 50)
        .onOutput(ProcessStreams.printToStdout())
        .start();
shell.writeLine("echo hello").writeLine("exit");
shell.await();
```

---

## Execution Modes

### Synchronous

`Warden.run(...).execute()` blocks the calling thread until the process exits. Stdout and stderr are drained on virtual threads in the background so large output never causes a pipe-buffer deadlock.

```java
ProcessOutcome outcome = Warden.run("mvn", "package")
        .timeout(Duration.ofMinutes(5))
        .onStdout(ProcessStreams.printToStdout())
        .onStderr(ProcessStreams.printToStderr())
        .execute();

switch (outcome) {
    case ProcessOutcome.Completed c -> System.out.println("exit code: " + c.exitCode());
    case ProcessOutcome.TimedOut  t -> System.err.println("timed out after " + t.elapsed());
    case ProcessOutcome.Killed    k -> System.err.println("cancelled after " + k.elapsed());
    case ProcessOutcome.Failed    f -> f.cause().printStackTrace();
}
```

### Asynchronous

`Warden.run(...).executeAsync()` launches the process and returns a `PipeHandle` immediately without blocking. The handle exposes an outcome future, direct stream access, and a `cancel()` method.

```java
PipeHandle handle = Warden.run("sleep", "30")
        .timeout(Duration.ofSeconds(10))
        .executeAsync();

// ... do other work ...

ProcessOutcome outcome = handle.await();
```

### Interactive PTY

`Warden.interactive(...).start()` launches the process under a pseudo-terminal so the child sees `isatty() == true`. Stdout and stderr are merged into a single PTY master stream. Use this for processes that depend on terminal behaviour — prompts, line-buffering, `ncurses` UIs.

```java
PtyHandle shell = Warden.interactive("python3")
        .ptySize(200, 50)
        .onOutput(ProcessStreams.printToStdout())
        .start();

shell.writeLine("print('hello from pty')");
shell.writeLine("exit()");
shell.await();
```

---

## Outcomes

Every execution resolves to a `ProcessOutcome`, a sealed interface with four variants:

| Variant | When |
|---|---|
| `Completed(exitCode, success, duration)` | Process exited naturally |
| `TimedOut(elapsed)` | Timeout expired; process was killed |
| `Killed(elapsed)` | Caller called `cancel()`; process was killed |
| `Failed(cause)` | Process never started, or an I/O error occurred |

`Completed.succeeded()` returns `true` when the exit code is zero.

---

## Builder Options

### `CommandBuilder` — via `Warden.run`

| Method | Description |
|---|---|
| `timeout(Duration)` | Kill the process after this duration. Default: 30 s. |
| `noTimeout()` | Disable the timeout. |
| `onStdout(StreamConsumer)` | Callback invoked with each raw chunk from stdout. |
| `onStderr(StreamConsumer)` | Callback invoked with each raw chunk from stderr. |
| `mergeOutputAndError()` | Merge stderr into stdout; a single stdout consumer receives both. |
| `redirectStdout(Path/File)` | Write stdout directly to a file. |
| `redirectStderr(Path/File)` | Write stderr directly to a file. |
| `redirectStdin(File)` | Feed stdin from a file. |
| `inheritIO()` | Inherit the parent process's stdin, stdout, and stderr. |
| `workingDir(Path)` | Set the working directory. |
| `env(key, value)` | Add an environment variable. |
| `envMap(Map)` | Add multiple environment variables. |
| `clearEnv()` | Clear the inherited environment before adding variables. |

### `PtyBuilder` — via `Warden.interactive`

| Method | Description |
|---|---|
| `timeout(Duration)` | Kill the PTY process after this duration. Default: none. |
| `noTimeout()` | Explicitly disable the timeout. |
| `ptySize(cols, rows)` | Set terminal dimensions. Default: 80 × 24. |
| `onOutput(StreamConsumer)` | Callback invoked with each raw chunk from the PTY output stream. |
| `workingDir(Path)` | Set the working directory. |
| `env(key, value)` | Add an environment variable. |
| `envMap(Map)` | Add multiple environment variables. |

---

## Stream Consumers

`StreamConsumer` is a functional interface that receives raw `byte[]` chunks as the process produces output. Pre-built implementations are available via `ProcessStreams`:

```java
ProcessStreams.printToStdout()                  // print live to System.out
ProcessStreams.printToStderr()                  // print live to System.err
ProcessStreams.toStringBuilder(StringBuffer sb) // accumulate into a buffer
ProcessStreams.toOutputStream(OutputStream out) // forward to any stream
ProcessStreams.lines(Consumer<String> action)   // fire once per complete line
ProcessStreams.tee(StreamConsumer... consumers) // fan out to multiple consumers
```

Custom consumers can be implemented directly:

```java
StreamConsumer consumer = chunk -> {
    // chunk is a defensive copy — safe to retain
    myBuffer.write(chunk);
};
```

---

## Handles

### `PipeHandle` — async processes

```java
PipeHandle handle = Warden.run("cat").noTimeout().executeAsync();

handle.writeLine("hello");      // write a line to stdin
handle.write("raw text");       // write without a newline
handle.write(new byte[]{0x1b}); // write raw bytes

handle.stdin();                 // raw OutputStream — flush manually
handle.stdout();                // raw InputStream — see ownership note below
handle.stderr();                // raw InputStream — see ownership note below

handle.isAlive();               // true while running
handle.cancel();                // SIGTERM → 3 s → SIGKILL; idempotent
handle.outcome();               // CompletableFuture<ProcessOutcome>
handle.await();                 // block until done
```

> `stdout()` and `stderr()` are safe to read directly only when no `onStdout` / `onStderr` consumer was configured. A configured consumer runs a background drainer on the same stream; concurrent reads produce corrupt, interleaved data.

### `PtyHandle` — PTY processes

```java
PtyHandle shell = Warden.interactive("bash").start();

shell.writeLine("ls -la");      // write a line to stdin
shell.write("\t");              // tab-complete, arrow keys, control sequences
shell.write(new byte[]{0x03}); // Ctrl-C

shell.stdin();                  // raw OutputStream
shell.output();                 // combined PTY output stream (stdout + stderr merged)

shell.resize(220, 50);          // resize terminal; sends SIGWINCH
shell.isAlive();
shell.cancel();                 // idempotent
shell.outcome();
shell.await();
```

---

## Design Notes

**No deadlocks on large output.** When a `StreamConsumer` is configured, Warden drains that stream on a virtual thread concurrently with `waitFor()`. Without a consumer, unused streams are discarded at the OS level via `ProcessBuilder.Redirect.DISCARD` — no pipe is allocated and the child process never blocks on a full buffer.

**Process tree cleanup.** `cancel()` and timeout expiry both invoke a tree reaper that snapshots all descendants, sends SIGTERM to every member of the tree, waits up to 3 seconds, then SIGKILLs any survivors. This prevents orphaned background processes.

**Virtual threads throughout.** Stream drainers, shutdown hooks, and the PTY output pump all run on virtual threads, keeping platform thread usage minimal.

**Outcome futures resolve after drains complete.** For async and PTY processes, the `CompletableFuture<ProcessOutcome>` does not resolve until all background drain tasks have finished. Output consumers will have received every byte before `await()` returns.
