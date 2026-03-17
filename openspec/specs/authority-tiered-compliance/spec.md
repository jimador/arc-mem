# Authority-Tiered Compliance Specification

## ADDED Requirements

### Requirement: Compliance strength mapping by authority

The system SHALL map each Authority level to a ComplianceStrength value. CANON and RELIABLE authorities MUST map to STRICT strength. UNRELIABLE authority MUST map to MODERATE strength. PROVISIONAL authority MUST map to PERMISSIVE strength.

#### Scenario: CANON authority maps to STRICT
- **WHEN** a CANON ARC Working Memory Unit (AWMU) is retrieved for prompt assembly
- **THEN** the compliance policy maps it to STRICT strength

#### Scenario: RELIABLE authority maps to STRICT
- **WHEN** a RELIABLE AWMU is retrieved
- **THEN** the compliance policy maps it to STRICT strength

#### Scenario: UNRELIABLE authority maps to MODERATE
- **WHEN** an UNRELIABLE AWMU is retrieved
- **THEN** the compliance policy maps it to MODERATE strength

#### Scenario: PROVISIONAL authority maps to PERMISSIVE
- **WHEN** a PROVISIONAL AWMU is retrieved
- **THEN** the compliance policy maps it to PERMISSIVE strength

### Requirement: Tiered prompt rendering

The prompt assembly process SHALL render AWMUs in authority-specific blocks, each with language matching the compliance strength. STRICT blocks SHALL use "MUST" language. MODERATE blocks SHALL use "SHOULD" language. PERMISSIVE blocks SHALL use "MAY" language.

#### Scenario: CANON facts render with MUST language
- **WHEN** prompt assembly renders CANON AWMUs
- **THEN** the prompt includes text like "CANON Facts (MUST be preserved)"
- **AND** CANON facts appear in their own section

#### Scenario: PROVISIONAL facts render with MAY language
- **WHEN** prompt assembly renders PROVISIONAL AWMUs
- **THEN** the prompt includes text like "PROVISIONAL Facts (MAY be reconsidered)"

#### Scenario: Authority tiers appear in correct order
- **WHEN** prompt is assembled with mixed-authority AWMUs
- **THEN** CANON block appears before RELIABLE, RELIABLE before UNRELIABLE, UNRELIABLE before PROVISIONAL

### Requirement: Configurable compliance policy

The system SHALL support configurable compliance policy via property `unit.compliance-policy`. The property MUST accept two values:
- DEFAULT: All AWMUs treated with STRICT compliance (flat behavior, backward compatible)
- TIERED: Authority-specific compliance strength as defined above

#### Scenario: DEFAULT policy uses flat compliance
- **WHEN** `unit.compliance-policy` is set to DEFAULT
- **THEN** all AWMUs render with STRICT compliance language
- **AND** no authority-specific blocks appear

#### Scenario: TIERED policy uses authority-specific compliance
- **WHEN** `unit.compliance-policy` is set to TIERED
- **THEN** AWMUs are grouped by authority
- **AND** each group renders with strength-appropriate language

#### Scenario: TIERED is default behavior
- **WHEN** application starts and `unit.compliance-policy` is not specified
- **THEN** the system defaults to TIERED mode

### Requirement: Policy implementation via interface

The system SHALL define a `CompliancePolicy` interface with single method `getStrengthFor(Authority)` that returns ComplianceStrength. Two implementations SHALL exist:
- `DefaultCompliancePolicy`: Returns STRICT for all authorities
- `AuthorityTieredCompliancePolicy`: Maps authority to strength as specified above

#### Scenario: Interface contract is enforced
- **WHEN** a custom CompliancePolicy is created
- **THEN** it implements `getStrengthFor(Authority)` and returns ComplianceStrength

#### Scenario: Implementations are injectable
- **WHEN** application context is initialized
- **THEN** the selected implementation is available via dependency injection

### Requirement: Backward compatibility

The `ArcMemLlmReference` public API SHALL remain unchanged. Callers of context-injection methods MUST observe no change in behavior with DEFAULT policy.

#### Scenario: API signature unchanged
- **WHEN** `ArcMemLlmReference.contextWithUnitsJinja()` is called
- **THEN** the method signature and return type are identical to previous version

#### Scenario: DEFAULT policy matches previous behavior
- **WHEN** DEFAULT policy is used
- **THEN** prompt output is identical to flat-compliance behavior from previous version

## Invariants

- **I1**: Every Authority level MUST map to exactly one ComplianceStrength
- **I2**: Authority enum changes MUST NOT require policy code changes (policy reads, does not define, authorities)
- **I3**: CANON/RELIABLE MUST use STRICT; UNRELIABLE MUST use MODERATE; PROVISIONAL MUST use PERMISSIVE
- **I4**: Prompt assembly MUST render authority blocks in order: CANON → RELIABLE → UNRELIABLE → PROVISIONAL

## REMOVED Requirements

### Requirement: Authority-tiered compliance prompt with revision carveout

**Reason**: Revision carveout removed — AWMU mutation is now HITL-only via UI. The prompt template SHALL NOT contain `[revisable]` annotations, `reviseFact` tool instructions, or revision exception blocks.
**Migration**: Remove all revision-related conditional blocks from `arc-mem.jinja`. Stop passing `revision_enabled` and `reliable_revisable` template variables.

#### Scenario: No revisable annotations in prompt

- **GIVEN** any configuration
- **WHEN** the prompt template is rendered
- **THEN** the output SHALL NOT contain `[revisable]` annotations on any AWMU entry

#### Scenario: No revision carveout in Critical Instructions

- **WHEN** the prompt template is rendered
- **THEN** the Critical Instructions section SHALL NOT contain `reviseFact` tool references

#### Scenario: No revision exception in Verification Protocol

- **WHEN** the prompt template is rendered
- **THEN** the Verification Protocol SHALL NOT contain a revision exception clause

### Requirement: Revision template variables

**Reason**: Template variables `revision_enabled` and `reliable_revisable` are no longer needed since all revision UI is HITL-only.
**Migration**: Remove `revision_enabled` and `reliable_revisable` from `ChatActions.respond()` and `ChatView.renderChatPrompt()`.
