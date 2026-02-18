# Implementation Tasks

## 1. Core Policy Framework

- [x] 1.1 Create `ComplianceStrength` enum (STRICT, MODERATE, PERMISSIVE)
- [x] 1.2 Create `CompliancePolicy` interface with `getStrengthFor(Authority)` method
- [x] 1.3 Implement `DefaultCompliancePolicy` (all authorities → STRICT)
- [x] 1.4 Implement `AuthorityTieredCompliancePolicy` (authority-specific mapping)

## 2. Configuration & Wiring

- [x] 2.1 Extend `DiceAnchorsProperties` to add `compliance-policy` field (enum: DEFAULT, TIERED)
- [x] 2.2 Create `ComplianceConfiguration` Spring @Configuration class
- [x] 2.3 Wire policy bean factory to select implementation based on property
- [x] 2.4 Update `application.yml` to set default policy to TIERED
- [x] 2.5 Add validation for enum at startup (fail fast if invalid)

## 3. Prompt Assembly Refactor

- [x] 3.1 Inject `CompliancePolicy` into `AnchorsLlmReference`
- [x] 3.2 Create `AuthorityTierBundle` record (authority, strength, anchors list)
- [x] 3.3 Refactor `contextWithAnchorsJinja()` to:
  - [x] 3.3.1 Group anchors by authority
  - [x] 3.3.2 Map each group through policy to get strength
  - [x] 3.3.3 Build AuthorityTierBundle list
  - [x] 3.3.4 Pass bundles to Jinja template
- [x] 3.4 Maintain backward-compatible API signature (no breaking changes)
- [x] 3.5 Add logging to trace policy selection and strength mapping

## 4. Template Updates

- [x] 4.1 Update `dice-anchors.jinja` to accept AuthorityTierBundle list
- [x] 4.2 Render authority-specific blocks:
  - [x] 4.2.1 CANON Facts block with MUST language
  - [x] 4.2.2 RELIABLE Facts block with SHOULD language
  - [x] 4.2.3 UNRELIABLE Facts block with cautionary language
  - [x] 4.2.4 PROVISIONAL Facts block with MAY language
- [x] 4.3 Order blocks by authority tier (CANON → RELIABLE → UNRELIABLE → PROVISIONAL)
- [x] 4.4 Skip empty tiers (don't render block if no anchors in that authority)

## 5. Testing

- [x] 5.1 Unit tests for `CompliancePolicy` implementations
  - [x] 5.1.1 Test `DefaultCompliancePolicy` maps all authorities to STRICT
  - [x] 5.1.2 Test `AuthorityTieredCompliancePolicy` maps each authority correctly
  - [x] 5.1.3 Test all Authority enum values are handled
- [x] 5.2 Unit tests for `AuthorityTierBundle` grouping logic
  - [x] 5.2.1 Test grouping anchors by authority
  - [x] 5.2.2 Test strength mapping per group
  - [x] 5.2.3 Test empty groups are excluded
- [x] 5.3 Integration test for DEFAULT vs TIERED policy comparison
  - [x] 5.3.1 Assemble prompt with DEFAULT policy
  - [x] 5.3.2 Assemble prompt with TIERED policy
  - [x] 5.3.3 Verify both are valid and contain expected language
- [x] 5.4 Integration test for prompt rendering
  - [x] 5.4.1 Verify CANON block appears before RELIABLE
  - [x] 5.4.2 Verify MUST/SHOULD/MAY language matches strength
  - [x] 5.4.3 Verify empty authority tiers are skipped

## 6. Verification

- [x] 6.1 Run full test suite: `./mvnw.cmd test`
- [x] 6.2 Build without tests: `./mvnw.cmd clean compile -DskipTests`
- [ ] 6.3 Manual smoke test: Start app, verify no errors on startup
- [ ] 6.4 Chat test: Send multiple messages, verify anchors are injected with tiered language
- [ ] 6.5 Demo comparison: Show DEFAULT vs TIERED prompts side-by-side in logs/traces
- [ ] 6.6 Toggle test: Change `anchor.compliance-policy` property, restart, verify behavior changes

## 7. Documentation & Cleanup

- [x] 7.1 Add Javadoc to `CompliancePolicy`, `ComplianceStrength`, `AuthorityTierBundle`
- [x] 7.2 Update CLAUDE.md or docs with compliance-policy configuration option
- [x] 7.3 Add example prompts showing both DEFAULT and TIERED output (in test comments or separate doc)
- [x] 7.4 Verify no debug logging left in code
- [x] 7.5 Code style check per CLAUDE.md (constructor injection, immutable collections, var usage)

## Definition of Done

- ✓ All tests pass (27+ total)
- ✓ No breaking changes to `AnchorsLlmReference` API
- ✓ DEFAULT policy produces flat compliance (backward compatible)
- ✓ TIERED policy produces authority-specific compliance blocks
- ✓ Policy is configurable via property
- ✓ Prompt demonstrates authority model visibly (CANON/RELIABLE/UNRELIABLE/PROVISIONAL blocks)
