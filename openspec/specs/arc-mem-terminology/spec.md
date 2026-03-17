## ADDED Requirements

### Requirement: Canonical terminology mapping
The project SHALL use the following terminology mapping consistently across all documentation, code comments, UI labels, prompt templates, and public API names:

| Old Term | New Term | Scope |
|----------|----------|-------|
| Context unit | Semantic unit or ARC Working Memory Unit (AWMU), depending on lifecycle stage | Documentation, UI, prompts |
| Context unit (class name) | MemoryUnit | Java class/interface names |
| ArcMemEngine | ArcMemEngine | Java class name |
| MemoryUnitRepository | MemoryUnitRepository | Java class name |
| ArcMemConfiguration | ArcMemConfiguration | Java class name |
| Rank | Activation score | Documentation, comments, UI labels |
| rank (field) | rank (unchanged) | Java field names, Neo4j properties |
| Budget / budget enforcement | Working-memory capacity | Documentation, UI labels |
| maxActiveUnits | maxActiveUnits | Configuration property names |
| Capacity eviction | Capacity eviction / deactivation | Documentation, UI labels |
| Authority promotion | Authority promotion | Documentation (unchanged behavior) |
| Pinned unit | Pinned unit | Documentation, UI labels |
| context-unit config namespace | arc-mem | Configuration property namespace |
| ContextTools | ContextTools | Java class name |
| ArcMemLlmReference | ArcMemLlmReference | Java class name |

Internal implementation names (private methods, local variables, Neo4j node labels) MAY retain legacy names where renaming would require data migration, provided a code comment explains the mapping.

#### Scenario: Documentation uses new terminology
- **WHEN** any documentation file (CLAUDE.md, blog, whitepaper, README, OpenSpec specs) references the working-memory system
- **THEN** it SHALL use "ARC-Mem", "semantic unit", "AWMU", "activation score", and "working-memory capacity" rather than legacy "context unit", "rank", or "budget" wording

#### Scenario: Java public API uses new class names
- **WHEN** a class or interface is part of the public API surface (non-private, referenced across packages)
- **THEN** it SHALL use the new class name from the mapping table

#### Scenario: Neo4j schema retains backward compatibility
- **WHEN** a Neo4j node label or property name currently uses legacy context-unit or rank terminology
- **THEN** it MAY retain the existing label/property name to avoid data migration
- **AND** the Java mapping layer SHALL translate between internal and public terminology

#### Scenario: Prompt templates use new terminology
- **WHEN** a prompt template in `src/main/resources/prompts/` contains LLM-facing text referencing the system
- **THEN** it SHALL use "semantic unit", "activation score", and "working-memory" terminology

### Requirement: ARC-Mem system description
All architecture documentation SHALL describe ARC-Mem as a **protected prompt-level working-memory layer** that maintains a **bounded working set of semantic units** extracted from conversation. The documentation SHALL present the pipeline as:

**Conversation → Semantic Unit Extraction (DICE) → ARC-Mem (Activation-Ranked Context Memory) → Structured Prompt Context → LLM Reasoning**

#### Scenario: Architecture documentation describes the pipeline
- **WHEN** architecture documentation describes the system's data flow
- **THEN** it SHALL present the four-stage pipeline: extraction, activation-ranked memory, prompt assembly, and LLM reasoning

#### Scenario: Documentation describes semantic unit generality
- **WHEN** documentation describes what ARC-Mem stores
- **THEN** it SHALL state that ARC-Mem operates over general semantic units including but not limited to propositions, entities, events, constraints, goals, and questions

### Requirement: ARC-Mem lifecycle description
Documentation SHALL describe the ARC-Mem lifecycle as: semantic units are **extracted** from conversation via DICE, **activation scores** are updated each turn based on recency, reinforcement, relevance, authority, and decay signals, **reinforced** units remain in the working set, **conflicts** between units are detected and mediated, and **low-activation** units are evicted when working-memory capacity is exceeded.

#### Scenario: Lifecycle documentation covers all phases
- **WHEN** documentation describes the ARC-Mem lifecycle
- **THEN** it SHALL mention extraction, activation scoring, reinforcement, conflict mediation, and capacity eviction as distinct phases

### Requirement: Semantic-unit and memory-unit distinction
The repository SHALL distinguish between extracted semantic content and promoted ARC-Mem-managed memory. `SemanticUnit` terminology SHALL refer to extracted or generic semantic-content helpers. `MemoryUnit` terminology SHALL refer to promoted ARC-Mem state and public API types centered on that promoted state.

#### Scenario: Promoted ARC-Mem state uses memory-unit terminology
- **GIVEN** the primary promoted ARC-Mem model or a public type centered on promoted-state persistence, lifecycle, or mutation
- **WHEN** its Java type name or documentation label is reviewed
- **THEN** it SHALL use `MemoryUnit` terminology

#### Scenario: Extracted semantic-content helpers use semantic-unit terminology
- **GIVEN** a helper that operates on extracted semantic content or semantic constraints before or around promotion
- **WHEN** its Java type name or documentation label is reviewed
- **THEN** it SHALL use `SemanticUnit` terminology rather than a generic `Unit` label

#### Scenario: Documentation describes both stages of the pipeline
- **WHEN** architecture documentation describes the extraction-to-memory pipeline
- **THEN** it SHALL describe semantic units as extracted content
- **AND** it SHALL describe AWMUs as promoted ARC-Mem-managed objects
