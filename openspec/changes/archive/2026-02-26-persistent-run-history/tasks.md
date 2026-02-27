## 1. Neo4j Benchmark Report Persistence

- [x] 1.1 Add `saveBenchmarkReport` Cypher MERGE in `Neo4jRunHistoryStore` — node label `BenchmarkReport`, first-class properties `reportId`, `scenarioId`, `createdAt`, `modelId`, JSON `payload` (spec: `run-history-persistence` → Neo4j benchmark report persistence)
- [x] 1.2 Add `loadBenchmarkReport` Cypher MATCH by `reportId` with JSON deserialization
- [x] 1.3 Add `listBenchmarkReports` Cypher MATCH ordered by `createdAt` DESC
- [x] 1.4 Add `listBenchmarkReportsByScenario` Cypher MATCH filtered by `scenarioId`
- [x] 1.5 Add `deleteBenchmarkReport` Cypher DETACH DELETE by `reportId`

## 2. Neo4j Experiment Report Persistence

- [x] 2.1 Add `saveExperimentReport` Cypher MERGE in `Neo4jRunHistoryStore` — node label `ExperimentReport`, first-class properties `reportId`, `createdAt`, JSON `payload` (spec: `run-history-persistence` → Neo4j experiment report persistence)
- [x] 2.2 Add `loadExperimentReport` Cypher MATCH by `reportId` with JSON deserialization
- [x] 2.3 Add `listExperimentReports` Cypher MATCH ordered by `createdAt` DESC
- [x] 2.4 Add `deleteExperimentReport` Cypher DETACH DELETE by `reportId`

## 3. Neo4j Baseline Persistence

- [x] 3.1 Add `saveAsBaseline` — MERGE `Baseline` node on `scenarioId`, create `BASELINE_FOR` relationship to `BenchmarkReport` node. Validate report exists, throw `IllegalArgumentException` if not (spec: `run-history-persistence` → Neo4j baseline persistence)
- [x] 3.2 Add `loadBaseline` — MATCH `Baseline` node by `scenarioId`, follow `BASELINE_FOR` to `BenchmarkReport`, deserialize
- [x] 3.3 Ensure `deleteBenchmarkReport` cascades baseline removal via `DETACH DELETE`

## 4. Default Store Configuration

- [x] 4.1 Update `application.yml` — change `dice-anchors.run-history.store` default from `MEMORY` to `NEO4J` (spec: `run-history` → Store selection via configuration)
- [x] 4.2 Update `SimulationConfiguration` — flip `matchIfMissing` so NEO4J is the default bean

## 5. Run History UI Panel

- [x] 5.1 Create `RunHistoryPanel` Vaadin component — collapsible panel with Grid showing runs from `RunHistoryStore.list()` (spec: `run-history-ui` → Run history panel)
- [x] 5.2 Add grid columns: scenario ID, completion date, turn count, resilience rate, model ID, injection status (spec: `run-history-ui` → Run history grid columns)
- [x] 5.3 Add scenario dropdown filter and injection-enabled toggle filter (spec: `run-history-ui` → Run history filtering)
- [x] 5.4 Add row click → navigate to `/run?runId={id}` (spec: `run-history-ui` → Navigate to run inspector)
- [x] 5.5 Add checkbox selection + "Compare" button → navigate to `/run?runId={a}&compare={b}`, disabled when selection != 2 (spec: `run-history-ui` → Compare two runs)
- [x] 5.6 Add delete action with confirmation dialog (spec: `run-history-ui` → Delete run from history)
- [x] 5.7 Add refresh button to reload from store
- [x] 5.8 Integrate `RunHistoryPanel` into `SimulationView` sidebar

## 6. Verification

- [x] 6.1 Add tests for `Neo4jRunHistoryStore` benchmark report CRUD (save/load/list/delete round-trips)
- [x] 6.2 Add tests for `Neo4jRunHistoryStore` experiment report CRUD
- [x] 6.3 Add tests for baseline save/load/cascade-delete
- [x] 6.4 Add test for report type isolation (benchmark list excludes experiments and vice versa)
- [x] 6.5 Verify `./mvnw clean compile -DskipTests` succeeds
- [x] 6.6 Manual verification: run simulation, restart app, confirm run appears in history panel

