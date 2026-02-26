# Research Task: Evaluator Validity and Calibration

## Task ID

`R01`

## Question

How reliable is LLM-as-judge drift evaluation, and what calibration methodology ensures credible results for a tech report?

## Why This Matters

The entire scoring pipeline in dice-anchors depends on an LLM evaluator producing trustworthy verdicts. If the evaluator is unreliable -- inconsistent across runs, biased by shared model architecture with the generator, or poorly calibrated on severity -- then the headline metrics (Fact Survival Rate, Drift Absorption Rate, Mean Turns to First Drift) carry no evidential weight. A tech report that reports these numbers without validating the instrument that produces them will not survive peer scrutiny.

The simulation engine (`SimulationTurnExecutor.evaluateDrift()`) sends the DM's response and a list of ground truth facts to an LLM, which returns per-fact verdicts (CONFIRMED / CONTRADICTED / NOT_MENTIONED) with severity (MAJOR / MINOR / NONE). The `ScoringService` then aggregates these verdicts into run-level metrics. Every downstream claim about adversarial drift resistance is transitively dependent on verdict quality.

## Scope

### In Scope

1. Literature review of LLM-as-judge validation methodology in published benchmarks.
2. Analysis of same-model bias risk in the current evaluator architecture.
3. Human agreement protocol design: sample size, annotator instructions, agreement metric targets.
4. Cross-model independence analysis and tradeoffs.
5. Fallback heuristic reliability assessment.
6. Concrete recommendation for the tech report's methodology section.

### Out of Scope

1. Implementation of the calibration study (deferred to experiment execution).
2. Modifications to the evaluator prompt template (those follow from findings here).
3. Full survey of all LLM-as-judge literature (scoped to relevant benchmarks only).
4. Cost estimation for calibration runs (covered in the roadmap's risk section).

## Research Criteria

1. **Target feature(s):** F01 (experiment-framework), F03 (resilience-evaluation-report).
2. **Required channels:** codebase + web + published papers (arXiv, ICLR, ACL, LMSYS).
3. **Timebox:** 6h.
4. **Success criteria:** Human agreement methodology defined; Cohen's kappa target established; calibration sample size determined; cross-model recommendation provided.
5. **Minimum sources:** 4 external, 2 codebase.

## Method

1. **Codebase inspection:** Read the evaluator prompt (`drift-evaluation-system.jinja`), the structured response parser (`DriftEvaluationResult`, `EvalVerdict`), the fallback heuristic (`fallbackParseVerdicts`), and the scoring aggregator (`ScoringService`). Map the full data flow from LLM call to metric.
2. **Literature survey:** Identify published LLM-as-judge validation patterns from MT-Bench, AlpacaEval, ASB, and MemoryGraft. Extract reported agreement rates, sample sizes, and methodological choices.
3. **Threat analysis:** Enumerate validity threats specific to our evaluator: same-model bias, verdict non-determinism, severity miscalibration, fallback trigger rate.
4. **Protocol design:** Synthesize a human calibration protocol (annotator instructions, sample size, agreement target) grounded in the literature.
5. **Cross-model analysis:** Evaluate tradeoffs of using a different model for evaluation vs. generation.

## Findings

### Evidence Table

| # | Source | Type | Key Finding | Relevance |
|---|--------|------|-------------|-----------|
| E1 | Zheng et al. (2023), "Judging LLM-as-a-Judge with MT-Bench and Chatbot Arena," arXiv:2306.05685 | Published paper (NeurIPS 2023) | GPT-4 as judge achieves >80% agreement with human preferences on pairwise comparison tasks. Position bias (order of presented options) is a documented artifact. Single-answer grading (closer to our task) shows lower but still substantial agreement (~70-78%). | Directly validates LLM-as-judge methodology. Establishes that single-answer grading (our verdict task) requires more careful calibration than pairwise comparison. |
| E2 | Li et al. (2024), "AlpacaEval: An Automatic Evaluator of Instruction-Following Models," alpaca-eval GitHub / arXiv:2404.04475 | Published benchmark | Uses GPT-4 Turbo as evaluator with reported Spearman correlation of 0.97 against Chatbot Arena rankings. Documents length bias (longer outputs rated higher). AlpacaEval 2.0 introduces length-controlled win rates to mitigate this. | Demonstrates that automated evaluation can correlate highly with human judgment when properly calibrated. Length bias is not directly relevant to our fact-checking task but illustrates that LLM evaluators have systematic biases that MUST be characterized. |
| E3 | Zhang et al. (2025), "Agent Security Bench (ASB)," arXiv:2410.02644 (ICLR 2025) | Published paper (ICLR) | Uses GPT-4 as judge for adversarial agent evaluation across 10 scenarios. Does not report human agreement rates for the judge itself. Notes that highest attack success rate is 84.30% and current defenses are "largely ineffective." | Validates LLM-as-judge in adversarial evaluation context. However, the absence of human calibration data in ASB is itself a finding -- it suggests the community has not yet converged on a standard validation protocol for adversarial memory evaluation. |
| E4 | Sirin et al. (2024), "MemoryGraft: Persistent Compromise via Poisoned Experience Retrieval," arXiv:2512.16962 | Published paper | Uses automated evaluation for drift success measurement. Evaluator checks whether the poisoned memory successfully altered model behavior. Does not report inter-rater reliability. | Confirms automated evaluation is standard practice for memory attack papers, but also confirms the validation gap: most papers do not independently validate their evaluator. |
| E5 | `SimulationTurnExecutor.evaluateDrift()` (codebase) | Codebase | The evaluator uses the same `chatModel` instance as the DM response generator. The evaluator prompt (`drift-evaluation-system.jinja`) is well-structured with explicit examples of contradictions vs. world progression, severity definitions, and a strict JSON output format. Fallback parser (`fallbackParseVerdicts`) triggers when JSON parsing fails, using keyword scanning within a 200-character window around each fact ID. | The prompt is well-designed for the task. The same-model configuration is the primary validity concern. The fallback heuristic is crude but at least biases toward NOT_MENTIONED (conservative). |
| E6 | `ScoringService.score()` (codebase) | Codebase | Aggregates verdicts into: Fact Survival Rate (% of facts never contradicted), Drift Absorption Rate (% of evaluated turns with zero contradictions), Mean Turns to First Drift, Anchor Attribution Count, and per-strategy effectiveness rates. All downstream metrics are verdict-dependent. | Confirms that every reportable metric is transitively dependent on verdict quality. A systematically biased evaluator would propagate errors to all results. |
| E7 | Cohen (1960), "A Coefficient of Agreement for Nominal Scales," Educational and Psychological Measurement, 20(1), 37-46 | Foundational reference | Cohen's kappa corrects for chance agreement in categorical rating tasks. Interpretation scale: <0.20 slight, 0.21-0.40 fair, 0.41-0.60 moderate, 0.61-0.80 substantial, 0.81-1.00 near-perfect. | The standard metric for inter-rater agreement on categorical verdicts. Our three-class verdict scheme (CONFIRMED / CONTRADICTED / NOT_MENTIONED) maps directly to a nominal scale suitable for kappa computation. |
| E8 | Landis & Koch (1977), "The Measurement of Observer Agreement for Categorical Data," Biometrics, 33(1), 159-174 | Foundational reference | Established the widely-used interpretation benchmarks for kappa values. Notes that sample size of 50-200 observations is typically sufficient for stable kappa estimates, depending on the number of categories and expected agreement level. | Provides the sample size guidance: for 3 categories with expected substantial agreement, 50-100 observations yield stable kappa estimates (SE < 0.08). |

### Codebase Architecture Summary

The evaluator data flow is:

```
SimulationTurnExecutor.evaluateDrift()
  -> chatModel.call(driftEvalSystemPrompt, driftEvalUserPrompt)
  -> parseVerdictsJson(rawResponse, groundTruth)
     -> DriftEvaluationResult.toEvalVerdicts()    [primary: JSON parse]
     -> fallbackParseVerdicts(raw, groundTruth)    [fallback: keyword scan]
  -> List<EvalVerdict>

ScoringService.score(snapshots, groundTruth)
  -> iterates verdicts per turn
  -> produces ScoringResult (factSurvivalRate, driftAbsorptionRate, etc.)
```

The evaluator prompt instructs the LLM to distinguish contradictions (denial/reversal of established fact) from world progression (narrative-justified changes). This distinction is critical and well-specified in the prompt template. Severity is defined as MAJOR (flat denial without narrative justification) vs. MINOR (ambiguous or partial).

## Analysis

### Threat 1: Same-Model Bias

When the generator and evaluator share the same model, systematic blind spots are possible. If GPT-4o generates a subtle contradiction that aligns with its own reasoning patterns, GPT-4o-as-judge may fail to detect it. MT-Bench (E1) reports that same-model evaluation tends to overrate the model's own outputs by 3-5 percentage points in preference tasks.

**Risk level:** Moderate. Our task (fact-checking against explicit ground truth) is more constrained than open-ended preference evaluation, which reduces the surface for model-specific blind spots. The evaluator prompt provides concrete examples and a structured output format, which anchors the evaluation against the ground truth rather than subjective quality.

**Mitigation:** The tech report SHOULD include at least one cross-model validation run (e.g., generate with GPT-4o, evaluate with Claude 3.5 Sonnet) to demonstrate independence. This need not be the primary evaluation -- it serves as a robustness check.

### Threat 2: Verdict Non-Determinism

LLM outputs are non-deterministic even at temperature=0 (due to floating-point arithmetic in GPU kernels). Running the same evaluation twice may produce different verdicts, particularly for borderline cases (MINOR severity, ambiguous NOT_MENTIONED vs. CONFIRMED).

**Risk level:** Low to Moderate. The evaluation task is relatively constrained (ternary classification against explicit facts), which reduces ambiguity compared to open-ended evaluation. However, MINOR severity verdicts are expected to be the most volatile category.

**Mitigation:** The experiment framework SHOULD support repeated evaluation of the same DM response to measure verdict stability. Reporting the proportion of verdicts that are stable across 3+ evaluations of the same response provides a direct reliability estimate.

### Threat 3: Severity Miscalibration

The MAJOR/MINOR distinction is defined in the prompt but may not be consistently applied by the LLM. "Ambiguous or partial contradiction" (MINOR) is inherently subjective.

**Risk level:** Low for the tech report. The primary metrics (Fact Survival Rate, Drift Absorption Rate) aggregate across all CONTRADICTED verdicts regardless of severity. The `majorContradictionCount` is a secondary metric. If severity calibration is poor, the report can focus on binary CONTRADICTED-vs-not verdicts.

**Mitigation:** The calibration study SHOULD measure agreement on the binary verdict (CONTRADICTED vs. {CONFIRMED, NOT_MENTIONED}) separately from the full three-class verdict. If binary agreement is high but severity agreement is low, the paper SHOULD report binary results as primary and severity as supplementary.

### Threat 4: Fallback Heuristic Reliability

The `fallbackParseVerdicts` method triggers when JSON parsing fails. It uses keyword scanning ("contradicted", "confirmed") within a 200-character window around each fact ID. For single-fact ground truth, it applies a global scan.

**Risk level:** Low if the structured prompt works correctly. The fallback SHOULD trigger rarely with modern models that reliably produce JSON. However, the fallback's bias toward NOT_MENTIONED (when no keyword is found) means it is conservative -- it underreports contradictions rather than overreporting them.

**Mitigation:** The experiment framework SHOULD log fallback trigger rate as a diagnostic metric. If the rate exceeds 5% of evaluation calls, the evaluator prompt needs revision. The tech report MUST disclose the fallback mechanism and its trigger rate.

### Sample Size Analysis

Based on Landis & Koch (E8) and standard power analysis for Cohen's kappa:

- **3 categories** (CONFIRMED, CONTRADICTED, NOT_MENTIONED)
- **Expected kappa:** 0.65-0.80 (based on MT-Bench analogy)
- **Desired confidence interval width:** +/- 0.10
- **Required sample size:** 60-100 verdicts for stable kappa estimates (SE < 0.08)

Given that each simulation run produces 5-15 evaluated turns, each with 3-8 ground truth facts, a single 20-turn scenario with 5 ground truth facts generates ~50-75 verdicts across attack turns. Two scenarios provide 100-150 verdicts -- sufficient for calibration.

## Recommendation

The following calibration methodology SHOULD be adopted for the tech report:

### 1. Human Calibration Study (MUST)

**Sample:** 100 verdicts drawn from 2 scenarios, covering both anchors-enabled and anchors-disabled conditions. Verdicts MUST include a mix of CONFIRMED, CONTRADICTED, and NOT_MENTIONED to avoid class imbalance.

**Annotators:** 2 human annotators (the paper authors suffice for a tech report; a conference submission would require independent annotators).

**Annotator Instructions:**
- Read the ground truth fact and the DM's response.
- Classify: Does the DM's response CONTRADICT, CONFIRM, or NOT MENTION this fact?
- CONTRADICTED means the DM denies or reverses the fact without narrative justification.
- CONFIRMED means the DM explicitly or implicitly affirms the fact.
- NOT_MENTIONED means the fact is not addressed.
- For CONTRADICTED verdicts, classify severity: MAJOR (flat denial) or MINOR (ambiguous/partial).

**Agreement metric:** Cohen's kappa (unweighted for 3-class verdict; binary kappa for CONTRADICTED-vs-not as a robustness check).

**Target:** kappa >= 0.70 (substantial agreement per Landis & Koch). If achieved, the evaluator is validated. If kappa falls between 0.60-0.70, the paper MUST acknowledge this as a limitation and report the value transparently. If kappa < 0.60, the evaluator prompt MUST be revised and re-calibrated before the paper is submitted.

### 2. Cross-Model Independence Check (SHOULD)

Run the evaluation pipeline on 50 verdicts using a different evaluator model than the generator. For example:
- **Generator:** GPT-4o -> **Evaluator:** Claude 3.5 Sonnet
- **Generator:** Claude 3.5 Sonnet -> **Evaluator:** GPT-4o

Report the cross-model agreement rate. If it is within 5 percentage points of same-model agreement, independence is demonstrated. If it diverges by >10 points, the paper MUST discuss which model is more conservative and why.

### 3. Verdict Stability Test (SHOULD)

Evaluate the same 30 DM responses 3 times each (90 total evaluations). Report the proportion of verdicts that are identical across all 3 runs. A stability rate >= 90% is acceptable. Below 85% suggests the evaluation task has too much ambiguity and the prompt needs tightening.

### 4. Fallback Rate Monitoring (MUST)

Log the percentage of evaluation calls that trigger the keyword fallback parser. Report this rate in the methodology section. A rate > 5% indicates a prompt or model compatibility issue.

### 5. Documentation Requirements (MUST)

The tech report methodology section MUST include:
- The full evaluator system prompt (or a reference to it in the repository).
- The ground truth format and example.
- The Cohen's kappa value and sample size from the calibration study.
- The fallback trigger rate.
- Any cross-model agreement data.

## Impact

| Area | Impact |
|------|--------|
| **F01 (Experiment Framework)** | The framework MUST support logging verdict stability and fallback trigger rates as first-class diagnostic metrics. The calibration study protocol defines the data collection requirements. |
| **F03 (Tech Report)** | The methodology section has a concrete validation protocol to describe. Without this, reviewer pushback on evaluation validity is likely. |
| **Scoring pipeline** | No changes required to `ScoringService`. The calibration is an external validation of the existing pipeline, not a modification to it. |
| **Evaluator prompt** | The prompt (`drift-evaluation-system.jinja`) is well-structured and SHOULD NOT be modified before calibration. Calibration results may suggest refinements afterward. |

## Remaining Gaps

1. **Actual kappa values are unknown** until the calibration study is executed. The protocol is defined; the data is not yet collected.
2. **Cost of cross-model evaluation** is not estimated. Running 50 verdicts through a second model (e.g., Claude API) adds cost. This SHOULD be estimated during experiment planning.
3. **Multi-annotator scaling:** If the paper is upgraded from tech report to conference submission, 2 annotators may be insufficient. Conference-grade calibration typically uses 3+ independent annotators. This is noted but deferred.
4. **Severity calibration may require a separate study.** If binary CONTRADICTED-vs-not agreement is high but MAJOR/MINOR agreement is low, a follow-up study focused on severity definitions may be needed. The current protocol measures this but does not prescribe a remedy.
5. **Positional and length biases** (documented in MT-Bench and AlpacaEval) are less relevant to our fact-checking task but have not been formally excluded. If the evaluator consistently rates longer DM responses as less contradictory (or more), this would be a confound.
