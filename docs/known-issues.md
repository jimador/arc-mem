<!-- sync: openspec/specs/anchor-conflict, openspec/specs/anchor-trust, openspec/specs/anchor-lifecycle, openspec/specs/share-ready-implementation-gate -->
<!-- last-synced: 2026-02-25 -->

# Known Issues

Current issue list for the demo implementation. Technical and direct by design.

## Confirmed limitations

| ID | Issue | Impact | Mitigation now | Next fix |
|---|---|---|---|---|
| L1 | Trust thresholds uncalibrated | profile behavior may drift across model/domain changes | conservative profiles | build labeled calibration set |
| L2 | Compaction is detect-only | protected fact loss can persist | validator warning events | add retry + extractive fallback |
| L3 | Token estimator is heuristic | strict budget behavior is approximate | acceptable for demo | switch to model-aware token counting |
| L4 | `NO_TRUST` missing | trust contribution not isolated | none | implement ablation condition |
| L5 | Drift judge uncalibrated vs humans | judge bias can masquerade as improvement | conservative interpretation | build adjudication set + agreement scoring |
| L6 | Generated attacks vary in quality | weak attacks can inflate robustness | deterministic pack exists | add quality gates/templates |
| L7 | Run manifest incomplete | reproducibility/debugging weaker than needed | partial metadata persisted | persist config/prompt hashes + seed |
| L8 | Revision classification unlabeled | false revision labels can leak bad edits | conservative conflict defaults | create labeled revision benchmark |
| L9 | Default mutation strategy is HITL-only | conflict resolver cannot auto-replace by default | intentional for demo safety | add additional strategies |
| L10 | Cross-session memory is limited | long-term continuity is weak | context isolation by design | optional long-term memory profile |

## Credibility blockers for stronger claims

Before stronger claim framing:
1. `NO_TRUST` results
2. calibrated drift judge
3. full run manifest provenance
4. deterministic vs stochastic evidence separation

## Already fixed

- Conflict parser fail-open path is fixed.
- Parse failures in conflict detection no longer silently promote contradictory candidates.

## Open research uncertainty (not defects)

- Better semantics for revision vs contradiction vs world progression.
- Best cascade strategy for dependent anchors after supersession.
- Temporal validity vs authority interaction rules.
- Cross-model generalization behavior.
