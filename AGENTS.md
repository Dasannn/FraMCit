## Agent Roles

- **Orchestrator:** Understand the task, decide whether to
  delegate, coordinate results, and verify the outcome.

- **Scout:** Locate relevant code, trace dependencies,
  callers, and tests. Investigate only; never edit files.

- **Builder:** Implement focused changes using verified
  context. Run relevant tests and report the results.

- **Reviewer:** Independently inspect changes for bugs,
  regressions, security issues, and missing tests.
  Do not edit files.

## Delegation Rules
- Simple tasks: handle directly.
- Unclear code ownership: use Scout.
- Well-defined, independent implementation: use Builder.
- High-risk changes: use Reviewer.
- Never delegate just to follow a workflow.
- Reuse findings instead of repeating investigations.
