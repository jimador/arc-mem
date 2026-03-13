## MODIFIED Requirements

### Requirement: Canonical terminology mapping

The project SHALL use the following terminology mapping consistently across all documentation, code comments, UI labels, prompt templates, and public API names:

| Old Term | New Term | Scope |
|----------|----------|-------|
| Context unit | Memory unit | Public Java names, docs, UI, prompts |
| MemoryUnit | MemoryUnit | Java class/interface names |
| MemoryUnitRepository | MemoryUnitRepository | Java class name |
| MemoryUnitLifecycleEvent | MemoryUnitLifecycleEvent | Java class name |
| MemoryUnitLifecycleListener | MemoryUnitLifecycleListener | Java class name |
| Unit promoter / unit constraint / generic unit helper | Semantic unit terminology where the type is about semantic content before or around promotion | Public Java names, docs |
| Rank | Activation score | Documentation, comments, UI labels |
| Budget / budget enforcement | Working-memory capacity | Documentation, UI labels |

Internal implementation names such as Neo4j labels and property names MAY retain legacy names where renaming would require data migration, provided code comments or mapping boundaries explain the compatibility choice.

#### Scenario: Java public API uses renamed types
- **WHEN** a public Java type centered on promoted ARC-Mem state is reviewed
- **THEN** it SHALL use `MemoryUnit` terminology rather than `MemoryUnit`

#### Scenario: Generic semantic helpers use semantic-unit names
- **WHEN** a public helper type is about semantic content rather than promoted memory state
- **THEN** it SHALL use `SemanticUnit` terminology rather than generic `Unit` terminology

#### Scenario: Documentation uses the renamed model
- **WHEN** documentation or UI text describes promoted ARC-Mem state
- **THEN** it SHALL use "memory unit"
- **AND** extracted content prior to promotion SHALL be described as "semantic unit"
