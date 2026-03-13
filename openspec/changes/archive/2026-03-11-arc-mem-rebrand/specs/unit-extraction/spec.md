## MODIFIED Requirements

### Requirement: Semantic unit extraction pipeline
The extraction pipeline SHALL be documented as the "DICE → ARC-Mem intake pipeline" — semantic unit extraction from conversation. Class renames: `AnchorPromoter` → `UnitPromoter`. `DuplicateDetector` name is terminology-neutral and MAY remain. Behavioral semantics (silent failure handling, intra-batch dedup, permissive fallback) remain unchanged.

#### Scenario: Extraction documentation uses DICE-to-ARC-Mem framing
- **WHEN** documentation describes the extraction pipeline
- **THEN** it SHALL describe it as "Conversation → DICE semantic unit extraction → ARC-Mem intake"
