## MODIFIED Requirements

### Requirement: Narrative generation

Narrative generation SHALL remain evidence-bound and SHALL include confidence qualifiers. When stability gates are unmet, required ablations are missing, or degraded runs materially affect results, the report SHALL use provisional language and SHALL explicitly block material-robustness claims.

#### Scenario: Missing ablation forces provisional narrative
- **GIVEN** a resilience report generated without all required ablations
- **WHEN** narrative text is composed
- **THEN** the narrative SHALL state that results are provisional
- **AND** material robustness claims SHALL be marked unsupported

#### Scenario: Stable claim-grade evidence allows non-provisional summary
- **GIVEN** a resilience report with required ablations, stability checks, and no blocking degradations
- **WHEN** narrative text is composed
- **THEN** the summary SHALL indicate claim-grade readiness

### Requirement: MarkdownReportRenderer

Markdown rendering SHALL include evidence-grade metadata and degraded-run indicators in the report header or summary blocks so reviewers can quickly assess evidence quality.

#### Scenario: Rendered report shows evidence grade and degraded-run indicators
- **GIVEN** a generated resilience report with degraded runs
- **WHEN** markdown is rendered
- **THEN** the output SHALL show evidence grade and degraded-run indicators in summary sections

## Invariants

- **RER1**: Resilience report narrative SHALL NOT overstate conclusions beyond validated evidence grade.
