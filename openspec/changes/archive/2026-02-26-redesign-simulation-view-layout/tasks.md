## 1. Header and Navigation

- [x] 1.1 Restructure header row: title left, Chat + Benchmark RouterLinks + theme toggle right-aligned
- [x] 1.2 Remove the standalone "Benchmark" RouterLink and replace with new nav link group

## 2. Controls Layout

- [x] 2.1 Split controls into two rows: Row 1 (Scenario ComboBox + Injection checkbox), Row 2 (Token Budget + Max Turns + action buttons)
- [x] 2.2 Implement contextual button visibility by SimControlState (IDLE: Run/History, RUNNING: Pause/Stop, PAUSED: Resume/Stop/History, COMPLETED: Run/History)

## 3. Scenario ComboBox Grouping

- [x] 3.1 Implement grouped Scenario ComboBox with category section headers (sorted alphabetically by category, then by title within each group)

## 4. Right Panel Tab Reorganization

- [x] 4.1 Remove Benchmark tab from right panel TabSheet
- [x] 4.2 Add "Scenario Details" tab as first tab, rendering the current Scenario Brief content (title, objective, focus, highlights, setting)
- [x] 4.3 Move DriftSummaryPanel from left column into a "Results" tab in the right panel
- [x] 4.4 Remove Scenario Brief Details component from above the split layout

## 5. Tab Auto-Selection

- [x] 5.1 Implement tab auto-selection: Scenario Details on load/scenario change, Context Inspector on run start, Results on completion, Manipulation on pause

## 6. Left Column Cleanup

- [x] 6.1 Remove DriftSummaryPanel from left column so ConversationPanel occupies full height

## 7. CSS Updates

- [x] 7.1 Update styles.css for two-row controls layout, button visibility transitions, and any tab styling adjustments

## 8. Verification

- [x] 8.1 Visual verification on 14" MacBook resolution (1512x982): controls fit, conversation not covered by results, tabs readable
