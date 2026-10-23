# FraMCit — Agent Flow

## Project context
- FraMCit is a Minecraft Paper 26.2 plugin using Java 25. Code and tests live in `plugin/`.
- `docs/constitution.md` defines product scope and non-negotiable security rules.
- Consult the relevant `docs/spec*.md` and `ARCHITECTURE.md` when the task
  affects product behavior or module contracts. Read only relevant context.
- `docs/plan.md` and `docs/tasks.md` describe previous phases, not mandatory
  gates for new work.

## Find the responsible code
- Before editing, trace entry points, symbols, callers, dependencies,
  relevant contracts, and tests. Verify findings against current code.
- Do not guess ownership from filenames or exhaustively scan by default.

## Implementation and delegation
- Prefer the smallest complete correct change. Fix clear, localized issues directly.
- For unclear scope, delegate focused, read-only investigation when useful.
- Use independent review for high-risk or security-sensitive changes.
- Avoid repeated investigations, unnecessary agents, and forced planning phases.
- Preserve architecture and conventions; avoid speculative abstractions.
- Never block Paper's main thread with network, image decoding, or disk I/O.
  Maintain URL protections, resource limits, persistence and optional dependencies.
- Do not change product scope, security guarantees, or major architecture
  without owner approval.
- Update durable documentation when behavior or architecture actually changes;
  do not generate routine specs or completion reports.

## Verification
- Run relevant focused tests; for Java plugin changes use
  `cd plugin && ./gradlew test` (Java 25 required).
- For packaging changes, also run `./gradlew shadowJar` inside `plugin/`.
- Inspect final diffs and report checks not run or residual risks.
- Do not claim unverified outcomes.

## Git, safety, and memory
- Use a task branch rather than `main`. Isolate concurrent changes in worktrees;
  never edit another agent's worktree.
- Never merge into `main` without explicit owner approval.
- Do not add agents as commit co-authors.
- Never commit secrets, temporary files, caches, or build artifacts.
- Use Engram only when available, for verified and reusable knowledge.
  Treat current code as more authoritative than remembered context.
  Never store secrets in memory.
