## ADDED Requirements

### Requirement: @Observed annotations on simulation operations

The following methods SHALL be annotated with `@Observed` from Micrometer: `SimulationService.runSimulation()` with name `simulation.run`, `SimulationTurnExecutor.executeTurn()` with name `simulation.turn`, `SimulationTurnExecutor.evaluateDrift()` (or equivalent extraction method) with name `simulation.extraction`, and the drift evaluator call with name `simulation.drift_evaluation`. Each observed method SHALL produce a Micrometer observation that is traceable via OTEL.

#### Scenario: simulation.run observation created
- **WHEN** `SimulationService.runSimulation()` is called
- **THEN** a Micrometer observation named `simulation.run` is started and stopped around the method execution

#### Scenario: simulation.turn observation per turn
- **WHEN** `SimulationTurnExecutor.executeTurn()` is called for turn 5
- **THEN** a Micrometer observation named `simulation.turn` is created for that invocation

### Requirement: OTEL span attributes per turn

Each `simulation.turn` span SHALL include the following OTEL span attributes: `sim.scenario` (scenario ID string), `sim.turn` (turn number integer), `sim.turn_type` (turn type string, e.g., ATTACK/ESTABLISH), `sim.strategy` (attack strategy string when applicable, empty otherwise), and `sim.target_fact` (target fact ID when applicable, empty otherwise). These attributes SHALL be set via the Micrometer Observation API's low-cardinality key-value pairs.

#### Scenario: Attack turn span attributes
- **WHEN** turn 7 executes as an ATTACK turn with strategy SUBTLE_REFRAME targeting fact "the-sword-is-cursed"
- **THEN** the span includes `sim.scenario=cursed-blade`, `sim.turn=7`, `sim.turn_type=ATTACK`, `sim.strategy=SUBTLE_REFRAME`, `sim.target_fact=the-sword-is-cursed`

#### Scenario: Establish turn span attributes
- **WHEN** turn 2 executes as an ESTABLISH turn with no strategy
- **THEN** the span includes `sim.turn_type=ESTABLISH`, `sim.strategy=`, `sim.target_fact=`

### Requirement: ChatModelHolder with ObservationRegistry

A `ChatModelHolder` class SHALL wrap the `ChatModel` with `ObservationRegistry` wired in so that Spring AI observations fire automatically on each LLM call. The holder SHALL implement `ChatModel` (delegating pattern) and SHALL support `switchModel(String modelName)` for per-turn model selection when the scenario specifies different models for generator vs. evaluator. `SimulationTurnExecutor` SHALL use `ChatModelHolder` instead of the raw `ChatModel`.

#### Scenario: LLM call observed
- **WHEN** `ChatModelHolder.call()` is invoked during a simulation turn
- **THEN** Spring AI chat observation fires and is captured by the ObservationRegistry

#### Scenario: Model switching
- **WHEN** a scenario specifies `generatorModel: gpt-4o` and `evaluatorModel: gpt-4o-mini`
- **THEN** `ChatModelHolder.switchModel()` is called before generation and before evaluation with the respective model names

### Requirement: SimulationLlmConfig

A `SimulationLlmConfig` `@Configuration` class SHALL wire `ChatModelHolder` with the `ObservationRegistry` bean. The configuration SHALL create the `ChatModelHolder` bean that wraps the application's primary `ChatModel`. The holder SHALL be injected into `SimulationTurnExecutor` and `SimulationService` in place of the raw `ChatModel`.

#### Scenario: ChatModelHolder bean available
- **WHEN** the Spring context initializes
- **THEN** a `ChatModelHolder` bean is available and wraps the primary ChatModel with observation support

### Requirement: Application configuration for observability

`application.yml` SHALL include the following configuration: `management.tracing.enabled=true`, `management.tracing.sampling.probability=1.0`, `spring.ai.chat.observations.include-input=true`, `spring.ai.chat.observations.include-output=true`. These settings SHALL enable full tracing with LLM input/output capture.

#### Scenario: Tracing enabled
- **WHEN** the application starts with default `application.yml`
- **THEN** tracing is enabled with 100% sampling probability

#### Scenario: LLM input/output captured
- **WHEN** a ChatModel call is made during simulation
- **THEN** the observation includes the input prompt and output response text

### Requirement: Langfuse OTEL exporter configuration

The application SHALL be configured to export OTEL traces to Langfuse via the `opentelemetry-exporter-langfuse` dependency. `application.yml` SHALL include `management.langfuse.enabled=true` with endpoint and API key configuration using environment variable defaults matching the docker-compose Langfuse stack (`pk-lf-dev-public` / `sk-lf-dev-secret`). The Maven `pom.xml` SHALL include `com.quantpulsar:opentelemetry-exporter-langfuse:0.4.0` and `embabel-agent-starter-observability` dependencies.

#### Scenario: Langfuse exporter configured
- **WHEN** the application starts with Langfuse environment variables set
- **THEN** OTEL traces are exported to the Langfuse endpoint

#### Scenario: Maven dependencies present
- **WHEN** `pom.xml` is inspected
- **THEN** it includes `opentelemetry-exporter-langfuse` (0.4.0), `embabel-agent-starter-observability`, and `opentelemetry-api` dependencies

### Requirement: OTEL span attributes for tier transitions

When a `TierChanged` lifecycle event is published, the system SHALL record OTEL span attributes on the active span (if present):
- `anchor.tier` (String): The new tier value (`HOT`, `WARM`, `COLD`)
- `anchor.tier.previous` (String): The previous tier value
- `anchor.id` (String): The anchor ID

These attributes SHALL be set as low-cardinality key-value pairs via the Micrometer Observation API.

#### Scenario: Tier upgrade span attributes

- **GIVEN** an active OTEL span during anchor reinforcement
- **WHEN** a `TierChanged` event is published with `previousTier = WARM` and `newTier = HOT`
- **THEN** the span SHALL include attributes `anchor.tier = "HOT"` and `anchor.tier.previous = "WARM"`

#### Scenario: No active span

- **GIVEN** no active OTEL span (e.g., background decay job)
- **WHEN** a `TierChanged` event is published
- **THEN** the event SHALL be logged at INFO level but no span attributes SHALL be set

### Requirement: Tier distribution in simulation turn spans

Each `simulation.turn` span SHALL include tier distribution attributes:
- `anchor.tier.hot_count` (int): Number of HOT anchors in the assembled prompt
- `anchor.tier.warm_count` (int): Number of WARM anchors in the assembled prompt
- `anchor.tier.cold_count` (int): Number of COLD anchors in the assembled prompt

#### Scenario: Turn span includes tier counts

- **GIVEN** a simulation turn assembling a prompt with 4 HOT, 8 WARM, and 3 COLD anchors
- **WHEN** the `simulation.turn` span is recorded
- **THEN** the span SHALL include `anchor.tier.hot_count = 4`, `anchor.tier.warm_count = 8`, `anchor.tier.cold_count = 3`
