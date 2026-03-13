## ADDED Requirements

### Requirement: Canonical terminology mapping
The project SHALL use the following terminology mapping consistently across all documentation, code comments, UI labels, prompt templates, and public API names:

| Old Term | New Term | Scope |
|----------|----------|-------|
| Anchor | Semantic unit (or "unit" in context) | All user-facing text, docs, UI |
| Anchor (class name) | ContextUnit | Java class/interface names |
| AnchorEngine | ArcMemEngine | Java class name |
| AnchorRepository | ContextUnitRepository | Java class name |
| AnchorConfiguration | ArcMemConfiguration | Java class name |
| Rank | Activation score | Documentation, comments, UI labels |
| rank (field) | rank (unchanged) | Java field names, Neo4j properties |
| Budget / budget enforcement | Working-memory capacity | Documentation, UI labels |
| maxActiveAnchors | maxActiveUnits | Configuration property names |
| Anchor eviction | Capacity eviction / deactivation | Documentation, UI labels |
| Anchor promotion | Authority promotion | Documentation (unchanged behavior) |
| Pinned anchor | Pinned unit | Documentation, UI labels |
| dice-anchors (config namespace) | arc-mem | Configuration property namespace |
| AnchorTools | ContextTools | Java class name |
| AnchorsLlmReference | ArcMemLlmReference | Java class name |

Internal implementation names (private methods, local variables, Neo4j node labels) MAY retain legacy names where renaming would require data migration, provided a code comment explains the mapping.

#### Scenario: Documentation uses new terminology
- **WHEN** any documentation file (CLAUDE.md, blog, whitepaper, README, OpenSpec specs) references the working-memory system
- **THEN** it SHALL use "ARC-Mem", "semantic unit", "activation score", and "working-memory capacity" rather than "anchor", "rank", or "budget"

#### Scenario: Java public API uses new class names
- **WHEN** a class or interface is part of the public API surface (non-private, referenced across packages)
- **THEN** it SHALL use the new class name from the mapping table

#### Scenario: Neo4j schema retains backward compatibility
- **WHEN** a Neo4j node label or property name currently uses "anchor" or "rank" terminology
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
