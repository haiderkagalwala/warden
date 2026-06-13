# Contributing to nexec

Thank you for taking the time to contribute. This document covers everything you need to get started.

---

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [How to Contribute](#how-to-contribute)
- [Development Setup](#development-setup)
- [Running the Tests](#running-the-tests)
- [Submitting a Pull Request](#submitting-a-pull-request)
- [Coding Conventions](#coding-conventions)
- [Reporting Bugs](#reporting-bugs)
- [Requesting Features](#requesting-features)

---

## Code of Conduct

Be respectful. Constructive criticism of code is welcome; personal attacks are not. Issues and PRs that violate this will be closed without discussion.

---

## How to Contribute

The workflow for external contributors is:

1. **Fork** the repository on GitHub (top-right button on the repo page)
2. **Clone** your fork locally
3. Create a **feature branch** off `main`
4. Make your changes
5. Push your branch to your fork
6. Open a **Pull Request** against `haiderkagalwala/nexec:main`

You do not need write access to the repository. Forks are the standard GitHub mechanism for external contributions.

---

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
- [ ] Update the Javadoc if you change public API
- [ ] Do not bump the version in `pom.xml` — that is handled during release

When you open the PR, fill in the template. Describe *what* the change does and *why* it is needed. Link the relevant issue if one exists.

Small, well-scoped PRs are reviewed faster than large ones.

---

## Coding Conventions

- Follow the existing style. The codebase uses standard Java formatting — 4-space indentation, no wildcard imports, `final` on local variables where practical.
- Internal implementation classes live in `io.github.haiderkagalwala.nexec.internal` and are package-private. Do not make them public.
- Public API changes (new methods, new types) should be discussed in an issue before implementation, especially anything that affects `CommandBuilder`, `PtyBuilder`, `ProcessOutcome`, or `StreamConsumer`.
- Javadoc is required on all public classes and methods. Fluent builder methods with self-evident names do not need `@param` / `@return` tags — a one-line description is sufficient.

---

## Reporting Bugs

Use the **Bug Report** issue template. Include:

- A minimal, self-contained code snippet that reproduces the problem
- The OS and Java version you are running
- What you expected to happen and what actually happened

Do not open a bug report for a question — use Discussions for that.

---

## Requesting Features

Use the **Feature Request** issue template. Describe the use case you are trying to solve, not just the API you want added. Understanding the problem helps find the best solution.
