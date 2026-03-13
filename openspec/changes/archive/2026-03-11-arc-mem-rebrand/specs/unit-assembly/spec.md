## MODIFIED Requirements

### Requirement: Context assembly with retrieval mode support
The context assembly pipeline SHALL be documented as "ARC-Mem context assembly" — activation-ranked semantic units assembled into structured prompt context. Class names `AnchorsLlmReference` → `ArcMemLlmReference`, `AnchorCacheInvalidator` → `ContextUnitCacheInvalidator`. Retrieval modes (BULK, HYBRID, TOOL) and behavioral semantics remain unchanged.

#### Scenario: Assembly documentation uses ARC-Mem framing
- **WHEN** documentation describes the assembly pipeline
- **THEN** it SHALL describe "activation-ranked semantic units assembled into structured prompt context"
- **AND** retrieval mode behavior SHALL remain identical
