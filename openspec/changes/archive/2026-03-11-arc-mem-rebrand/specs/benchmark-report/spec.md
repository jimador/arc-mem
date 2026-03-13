## MODIFIED Requirements

### Requirement: Benchmark report terminology update
Benchmark report labels, column headers, and descriptions SHALL use ARC-Mem terminology. "Anchor count" → "active unit count", "anchor survival" → "unit retention". `BenchmarkReport` record name is terminology-neutral and MAY remain. Report structure and statistical methods remain unchanged.

#### Scenario: Report labels use ARC-Mem terms
- **WHEN** a benchmark report is rendered (UI or Markdown)
- **THEN** all labels SHALL use ARC-Mem terminology per the canonical mapping
