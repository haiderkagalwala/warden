# Changelog

All notable changes to nexec are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).
nexec uses [Semantic Versioning](https://semver.org/).

---

## [0.1.0] — 2025-06-13

Initial public release.

### Added

- `Nexec.run(String...)` — fluent builder for normal (non-PTY) process execution
- `CommandBuilder` — configures command, working directory, environment, streams, timeout, exit codes, and I/O redirects
- `Nexec.interactive(String...)` — fluent builder for PTY (pseudo-terminal) process execution via pty4j (optional dependency)
- `PtyBuilder` — configures PTY dimensions, Windows backend (WinPTY / ConPTY), ANSI colour support, and output stream
- `SyncExecutionEngine` — blocks the calling thread until the process exits; drains streams on virtual threads to prevent 64 KB pipe-buffer deadlock
- `AsyncExecutionEngine` — launches process without blocking; returns a `PipeHandle` immediately
- `PtyExecutionEngine` — launches PTY process without blocking; returns a `PtyHandle` immediately
- `PipeHandle` — handle to an async process: write to stdin, read streams, `cancel()`, `await()`, `outcome()`
- `PtyHandle` — handle to a PTY process: write to stdin, `resize(cols, rows)`, `cancel()`, `await()`, `outcome()`
- `ProcessOutcome` — sealed interface with four variants: `Completed`, `TimedOut`, `Killed`, `Failed`
- `StreamConsumer` — functional interface for receiving raw byte chunks from a process stream
- `ProcessStreams` — factory for common consumers: `printToStdout()`, `printToStderr()`, `toStringBuilder()`, `toOutputStream()`, `lines()`, `tee()`
- `TreeReaper` — SIGTERM → 3 s grace → SIGKILL process tree teardown for non-PTY processes
- `PtyTreeReaper` — SIGTERM → 3 s grace → SIGKILL for PTY processes
- Shutdown hook registration in both async engines to kill the process tree if the JVM exits while the process is running
- `successExitCodes(int...)` on both builders — configurable set of exit codes treated as successful (default: `{0}`)
- `clearEnv()` on `CommandBuilder` — clears the inherited environment before adding variables
- `ProcessBuilder.Redirect.DISCARD` optimisation in sync mode — streams with no consumer and no file redirect are discarded at the OS level, not via a drain thread
- Full test suite: 56 tests across `SyncExecutionTest`, `AsyncExecutionTest`, `ProcessOutcomeTest`, `ProcessStreamsTest`
- Cross-platform test infrastructure via `TestProcessHelper` (JVM subprocess) — no shell dependency

[0.1.0]: https://github.com/haiderkagalwala/nexec/releases/tag/v0.1.0
