## ADDED Requirements

### Requirement: CSS class naming convention
All view-layer CSS classes MUST use the `ar-` prefix (anchor-retro) with BEM-lite naming: `ar-{block}` or `ar-{block}--{modifier}`. No `__element` nesting — Vaadin components serve as elements.

#### Scenario: New CSS class follows naming convention
- **WHEN** a developer adds a new CSS class for a view component
- **THEN** the class name MUST start with `ar-` and follow the `ar-{block}--{modifier}` pattern

#### Scenario: Existing styles.css classes are preserved
- **WHEN** migrating inline styles to CSS classes
- **THEN** existing class names in `styles.css` (e.g., `.sim-turn-bubble`, `.drift-summary-grid`, `.event-badge`) MUST be preserved alongside new `ar-` classes to avoid breaking any current usage

### Requirement: No new inline styles
View files MUST NOT introduce new `getStyle().set()` calls. All styling MUST be applied via CSS classes using `addClassName()` or via `data-*` attributes with CSS attribute selectors.

#### Scenario: Inline style added to a view file
- **WHEN** a code change adds a `getStyle().set()` call to any file in `sim/views/` or `chat/`
- **THEN** the change MUST be rejected in review — the style belongs in `styles.css`

#### Scenario: Dynamic color based on runtime data
- **WHEN** a component's color depends on runtime data (verdict, turn type, health threshold)
- **THEN** the component MUST use `getElement().setAttribute("data-*", value)` and CSS attribute selectors instead of inline `getStyle().set("color", ...)`

### Requirement: Inline style migration completeness
All existing `getStyle().set()` calls in sim view files and `ChatView.java` MUST be replaced with CSS class equivalents. Zero inline style calls SHALL remain after migration.

#### Scenario: Panel with inline styles after migration
- **WHEN** migration of a panel is marked complete
- **THEN** the panel file MUST contain zero `getStyle().set()` calls (verified by grep)

#### Scenario: Visual appearance preserved
- **WHEN** inline styles are migrated to CSS classes
- **THEN** the rendered appearance in both dark and light themes MUST be identical to the pre-migration state

### Requirement: Dynamic accent colors via data attributes
Components that vary color based on a finite set of runtime values (verdict, turn type, health level) MUST use `data-*` attributes and CSS attribute selectors.

#### Scenario: Verdict-colored badge
- **WHEN** a badge displays a drift verdict (CONFIRMED, CONTRADICTED, NOT_MENTIONED)
- **THEN** the component MUST set `data-verdict="confirmed|contradicted|not-mentioned"` and the CSS MUST define `.ar-badge[data-verdict="..."]` selectors for each value

#### Scenario: Turn type badge
- **WHEN** a badge displays a turn type (WARM_UP, ESTABLISH, ATTACK, DRIFT, DISPLACEMENT, RECALL_PROBE)
- **THEN** the component MUST set `data-turn-type="..."` and CSS MUST define matching attribute selectors

#### Scenario: Metric health color
- **WHEN** a metric card displays a health-colored accent (good, warn, bad)
- **THEN** the component MUST set `data-health="good|warn|bad"` and CSS MUST define matching attribute selectors

### Requirement: CSS file organization
New CSS classes in `styles.css` MUST be grouped by component/panel with section comment headers matching the existing file structure.

#### Scenario: Adding classes for ConversationPanel
- **WHEN** CSS classes for ConversationPanel badges are added
- **THEN** they MUST appear under a `/* ── Conversation panel ── */` section comment, following the existing pattern (e.g., `/* ── Timeline redesign ── */`)
