## ADDED Requirements

### Requirement: Post-compaction fact survival validation

After `SimSummaryGenerator` produces a compaction summary, the system SHALL validate that all protected content items survived in the summary. Validation SHALL normalize both the protected content text and summary text (lowercase, strip non-alphanumeric) and check for substring containment. Any protected content not found in the summary SHALL be reported as a `CompactionLossEvent` on the turn result.

#### Scenario: All protected facts survive
- **WHEN** compaction produces a summary and all protected anchor texts appear in the summary
- **THEN** zero CompactionLossEvents are reported

#### Scenario: Protected fact missing from summary
- **WHEN** compaction produces a summary and a protected anchor with text "Baron Krell rules the Northern Province" is not found in the summary
- **THEN** a CompactionLossEvent is reported identifying the missing anchor

#### Scenario: Partial match counts as survival
- **WHEN** a protected anchor states "Baron Krell rules the Northern Province" and the summary contains "baron krell" and "northern province"
- **THEN** the anchor is considered to have survived (normalized substring match)

### Requirement: CompactionLossEvent record

A `CompactionLossEvent` record SHALL contain: `anchorId` (String), `anchorText` (String), `authority` (Authority), and `rank` (int). CompactionLossEvents SHALL be included in the turn result and visible in the Compaction tab of the ContextInspectorPanel.

#### Scenario: Loss event displayed in UI
- **WHEN** a CompactionLossEvent occurs on turn 8
- **THEN** the Compaction tab for turn 8 shows the lost anchor's text, authority, and rank
