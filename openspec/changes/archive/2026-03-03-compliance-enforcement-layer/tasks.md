## 1. ComplianceAction enum + ComplianceViolation record

- [x] 1.1 Create `ComplianceAction` enum in `assembly/` with values `ACCEPT`, `RETRY`, `REJECT` (REQ-ACTIONS)
- [x] 1.2 Create `ComplianceViolation` record in `assembly/` with components: `anchorId`, `anchorText`, `anchorAuthority`, `description`, `confidence` (REQ-VIOLATION)

## 2. ComplianceContext record with nested CompliancePolicy

- [x] 2.1 Create `ComplianceContext` record in `assembly/` with components: `responseText`, `activeAnchors`, `policy` (REQ-CONTEXT)
- [x] 2.2 Create nested `CompliancePolicy` record with per-authority enforcement toggles: `enforceCanon`, `enforceReliable`, `enforceUnreliable`, `enforceProvisional` (REQ-POLICY)
- [x] 2.3 Add `canonOnly()` factory method returning `new CompliancePolicy(true, false, false, false)` (REQ-POLICY)
- [x] 2.4 Add `tiered()` factory method returning `new CompliancePolicy(true, true, false, false)` (REQ-POLICY)

## 3. ComplianceResult record + ComplianceEnforcer interface

- [x] 3.1 Create `ComplianceResult` record in `assembly/` with components: `compliant`, `violations`, `suggestedAction`, `validationDuration` (REQ-RESULT)
- [x] 3.2 Add `compliant(Duration)` static factory returning `new ComplianceResult(true, List.of(), ACCEPT, duration)` (REQ-RESULT)
- [x] 3.3 Create `ComplianceEnforcer` `@FunctionalInterface` in `assembly/` with single method `enforce(ComplianceContext) -> ComplianceResult` (REQ-INTERFACE)
- [x] 3.4 Verify interface is non-sealed and has no other abstract methods (REQ-OPEN)

## 4. PromptInjectionEnforcer (@Primary default)

- [x] 4.1 Create `PromptInjectionEnforcer` class in `assembly/` implementing `ComplianceEnforcer`, annotated `@Primary @Component` (REQ-DEFAULT)
- [x] 4.2 Implement `enforce()` to always return `ComplianceResult.compliant(Duration.ZERO)` (REQ-DEFAULT)

## 5. PostGenerationValidator (LLM-based validation)

- [x] 5.1 Create `PostGenerationValidator` class in `assembly/` implementing `ComplianceEnforcer`, annotated `@Component` (REQ-VALIDATOR)
- [x] 5.2 Implement authority-based anchor filtering via `CompliancePolicy` toggles (REQ-VALIDATOR)
- [x] 5.3 Implement fast-path: return `compliant` immediately when no anchors match policy (REQ-VALIDATOR)
- [x] 5.4 Implement single validation prompt containing all filtered anchors and response text (REQ-VALIDATOR)
- [x] 5.5 Implement LLM call via constructor-injected `ChatModel` (REQ-VALIDATOR, REQ-THREAD-SAFE)
- [x] 5.6 Implement violation parsing with `@JsonIgnoreProperties(ignoreUnknown = true)` on all response models (REQ-PARSE-LENIENT)
- [x] 5.7 Implement fail-open on parse failure: treat as compliant, log warning (REQ-PARSE-LENIENT)
- [x] 5.8 Implement `determineAction()`: REJECT for CANON violations, RETRY for lower authority, ACCEPT for no violations (REQ-CANON-REJECT)
- [x] 5.9 Implement structured logging: `compliance.check` at INFO, `compliance.violation` at WARN (REQ-VALIDATOR)

## 6. Verification

- [x] 6.1 All existing tests pass (`./mvnw test`)
- [x] 6.2 Compile clean with no warnings
- [x] 6.3 `ComplianceEnforcer` has exactly one abstract method (functional interface contract)
- [x] 6.4 `PromptInjectionEnforcer` is `@Primary` and always returns compliant
- [x] 6.5 `PostGenerationValidator` filters anchors by policy before validation
- [x] 6.6 All LLM response models use `@JsonIgnoreProperties(ignoreUnknown = true)`
