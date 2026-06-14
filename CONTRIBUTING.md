# Contributing to nexec

Thank you for taking the time to contribute. This document covers everything you need to get started.



## Development Setup

**Requirements:**

- Java 21 or later
- Maven 3.9 or later

**Clone your fork:**

```bash
git clone https://github.com/YOUR_USERNAME/nexec.git
cd nexec
```

**Build and run tests:**

```bash
mvn verify
```

This compiles the library, compiles the tests, and runs the full test suite. A clean build with all tests passing is the baseline — your PR must achieve the same.

**Optional PTY dependency:**

If you are working on PTY-related code (`Nexec.interactive()`), you need pty4j on the classpath. It is already declared as an optional dependency in `pom.xml` and will be pulled in automatically by Maven for test compilation.

---

## Running the Tests

```bash
# Run everything
mvn test

# Run a single test class
mvn test -Dtest=SyncExecutionTest

# Run a single test method
mvn test -Dtest=SyncExecutionTest#timeout_returnsTimedOut
```

The test suite uses a self-contained `TestProcessHelper` as the subprocess target — no external tools (bash, python, etc.) are required. Tests run identically on Windows, Linux, and macOS.

---

## Submitting a Pull Request

Before opening a PR, please:

- [ ] Run `mvn verify` locally — all tests must pass
- [ ] Add tests for any new behaviour or bug fix
- [ ] Keep the scope of the PR focused — one feature or fix per PR
- [ ] Update the Javadoc if you change the public API
- [ ] Do not bump the version in `pom.xml` — that is handled during release

---

## Coding Conventions

- Follow the existing style. The codebase uses standard Java formatting — 4-space indentation, no wildcard imports, `final` on local variables where practical.
- Internal implementation classes live in `io.github.haiderkagalwala.nexec.internal` and are package-private. Do not make them public.
- Public API changes (new methods, new types) should be discussed in an issue before implementation, especially anything that affects `CommandBuilder`, `PtyBuilder`, `ProcessOutcome`, or `StreamConsumer`.
- Javadoc is required on all public classes and methods. Fluent builder methods with self-evident names do not need `@param` / `@return` tags — a one-line description is sufficient.

---

## Reporting Bugs

Use the **Bug Report** issue template.

## Requesting Features

Use the **Feature Request** issue template.
