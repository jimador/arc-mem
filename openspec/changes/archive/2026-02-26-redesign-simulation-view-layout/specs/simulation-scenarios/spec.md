## MODIFIED Requirements

### Requirement: Two-level scenario selection by category
The scenario selection SHALL use two separate components: a Category `Select<String>` and a Scenario `ComboBox<SimulationScenario>`. Selecting a category SHALL filter the Scenario ComboBox to show only scenarios in that category. Categories SHALL be sorted alphabetically (via TreeMap), with scenarios sorted by title within each category.

#### Scenario: Category selection filters scenarios
- **WHEN** the user selects a category from the Category Select
- **THEN** the Scenario ComboBox is filtered to show only scenarios belonging to that category, sorted by title

#### Scenario: Categories sorted alphabetically
- **WHEN** the SimulationView loads
- **THEN** the Category Select contains categories in alphabetical order (adversarial, baseline, compaction, dormancy, extraction, multi-session, trust)
