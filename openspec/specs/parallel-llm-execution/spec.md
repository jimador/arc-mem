## ADDED Requirements

### Requirement: LlmCallService provides concurrent LLM invocation
The system SHALL provide an `LlmCallService` that wraps LLM model calls for use within structured concurrency scopes. The service MUST be injectable via constructor injection. The service MUST use virtual threads for all blocking LLM I/O operations.

#### Scenario: Single async call within structured scope
- **WHEN** a caller invokes `LlmCallService.call(systemPrompt, userPrompt)` within a `StructuredTaskScope`
- **THEN** the call SHALL execute on a virtual thread and return the LLM response as a `String`

#### Scenario: Call timeout
- **WHEN** an LLM call exceeds the configured timeout (`arc-mem.llm.call-timeout-seconds`, default 60)
- **THEN** the call SHALL throw a `TimeoutException` and the virtual thread SHALL be interrupted

#### Scenario: Call failure propagation
- **WHEN** an LLM call fails with an exception within a `StructuredTaskScope.ShutdownOnFailure`
- **THEN** the scope SHALL shut down all other subtasks and propagate the exception to the caller

### Requirement: LlmCallService is thread-safe
The `LlmCallService` MUST be safe for concurrent use from multiple virtual threads. It SHALL NOT maintain any mutable per-call state. All call parameters MUST be passed as method arguments.

#### Scenario: Concurrent calls from parallel branches
- **GIVEN** two parallel branches in a `StructuredTaskScope`
- **WHEN** both branches invoke `LlmCallService.call()` concurrently
- **THEN** both calls SHALL execute independently without interference or shared state corruption

## Invariants
- I1: LlmCallService SHALL NOT block platform threads; all blocking I/O MUST occur on virtual threads
- I2: No mutable shared state between concurrent calls
