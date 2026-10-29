# ADR-045 - Project layout and execution boundaries

**Date**: 2026-10-29
**Status**: ACCEPTED
**Decision**: Target structure and real decoupling approved by the project owner.

## Context

Project documentation was coupled to the Claude harness, all Maven modules lived
at the root, and `platform-app` was difficult to distinguish from
`platform-application`. Transport contracts shared an artifact with network
adapters. The execution engine owned performance phase ordering and load-model
configuration; the agent constructed assertion sampling directly.

## Decision

- `docs/knowledge/` owns project specifications, architecture and ADRs.
  `.claude/skills/<name>/SKILL.md` owns discoverable harness workflows.
- `core/` contains framework-free domain, use cases, plugin, transport and report
  contracts. `platform-application` becomes `platform-use-cases`.
- `execution/` contains DAG dispatch, retry, correlation and the agent runtime.
  Its production dependencies on platform modules are restricted to core contracts.
- `performance/` owns scenario validation, campaign phase ordering, assertion
  sampling and performance report assessment.
- `infra/` owns YAML parsing, transport implementations, persistence, task
  adapters, Gatling, report rendering/output and observability.
- `bootstrap/` (`platform-bootstrap`) is the composition root and exposes REST,
  UI and CLI. It remains the only production executable.
- `distribution/` combines Docker, Kubernetes, example scenarios and demo SUTs.
  Demo applications remain independently built systems under test, outside the
  platform Maven reactor.

## Dependency inversion

`platform-transport-api` contains channels and messages without network libraries.
Execution and agent code depend on it, not on `platform-transport` implementations.

`ExecutionPlanBuilder` moves to the core use-case ports. Its performance-specific
implementation and the campaign `ExecutionEngine` move to
`com.performance.platform.campaign`. The existing DAG executor, dispatchers and
correlation machinery remain in the generic execution module.

`TaskAgentRuntime` receives a mandatory `LifecycleSignalHandler` instead of
constructing assertion policy. Bootstrap wires the performance sampling handler
in both LOCAL and DISTRIBUTED modes. This constructor change is intentional;
task executor and transport contracts, YAML syntax and persisted schemas do not change.

`ReportOutput` separates report assembly from file output and renderers.
`ScenarioParser` remains a core port; SnakeYAML/Jackson parsing moves to its adapter.
Package names for other existing contracts remain stable to limit plugin breakage.

## Constraints

- Core still describes the platform's scenario/task vocabulary, including phases.
  Generic execution accepts those descriptors but does not choose performance
  phases, interpret load models, evaluate SLOs or create assertion policies.
  This is not a new domain-neutral workflow product or a separate distribution.
- Root Maven aggregation remains flat: directories are responsibility groups, not
  intermediate Maven parents. Nested modules explicitly reference `../../pom.xml`.
- No symlink or duplicate legacy module directories are retained.
- Bytecode boundary tests and reactor-structure tests guard the separation.
- Build, Compose, Docker, scripts, examples and documentation use the new paths.

## Consequences

The reactor grows from 14 to 20 modules to represent actual contract/adapter and
execution/policy boundaries. Existing Java packages are retained where moving a
responsibility does not require changing its namespace. The campaign namespace
changes so bytecode tests can enforce its separation from generic execution.

LOCAL/DISTRIBUTED behavior, scenario syntax, plugin discovery and the single
`performance-platform.jar` artifact remain unchanged.
