## Context

Current simulation flow: seed anchors → execute turns (LLM call + drift eval) → score. DICE extraction only runs in the chat flow via async `ConversationPropositionExtraction` event listener. The simulation never extracts propositions from DM responses, so the full DICE pipeline is invisible during sim runs. For the DICE/Embabel demo, we need extraction visible and integrated.

The extraction pipeline already exists:
- `LlmPropositionExtractor` (uses `extract_dnd_propositions.jinja`)
- `PropositionReviser` (dedup)
- `AnchorRepository.save()` (persist)
- `AnchorPromoter.evaluateAndPromote()` (full pipeline)

We just need to call it from `SimulationTurnExecutor`.

## Goals / Non-Goals

**Goals:**
- Run DICE extraction on DM responses during simulation when enabled
- Use the same promotion pipeline as chat (AnchorPromoter)
- Show extraction metadata in ContextTrace and UI
- Create showcase scenarios demonstrating extraction → promotion → drift resistance
- Maintain backward compatibility (existing scenarios unchanged)

**Non-Goals:**
- Add incremental analysis (full extraction per turn is fine for sim)
- Modify the extraction templates
- Change scoring or drift evaluation (extracted anchors participate in drift eval naturally)
- Add extraction to ALL scenarios (opt-in only)

## Decisions

### 1. Extraction Integration Point

After DM response, before drift evaluation:

```
executeTurnFull():
  1. Build prompts
  2. Call LLM → dmResponse
  3. Build ContextTrace
  4. IF extractionEnabled:
     a. Extract propositions from dmResponse
     b. Persist to Neo4j (same contextId)
     c. evaluateAndPromote() → some become anchors
     d. Record extraction results in ContextTrace
  5. Evaluate drift (now includes newly-promoted anchors)
  6. Diff anchor state
```

**Why**: Extract before drift eval so new anchors are visible in verdicts. Natural ordering.

**Alternative considered**: Extract after drift eval (misses showing new anchors in same turn's evaluation).

### 2. Extraction Call Pattern

Create a `SimulationExtractionService` that wraps the extraction pipeline for sim context:

```java
@Service
public class SimulationExtractionService {

    private final LlmPropositionExtractor extractor;
    private final AnchorRepository repository;
    private final AnchorPromoter promoter;

    public ExtractionResult extract(String contextId, String dmResponse) {
        // 1. Extract propositions from DM response text
        var propositions = extractor.extract(dmResponse);

        // 2. Persist each proposition
        for (var prop : propositions) {
            repository.save(prop.withContextId(contextId));
        }

        // 3. Run promotion pipeline
        int promoted = promoter.evaluateAndPromote(contextId, propositions);

        return new ExtractionResult(propositions.size(), promoted);
    }
}
```

**Why**: Isolates sim extraction from chat extraction. Avoids touching async event listener. Clean constructor injection.

**Alternative considered**: Reuse `ConversationPropositionExtraction` directly (requires Conversation object, which sim doesn't have).

### 3. Scenario YAML Extension

Add optional `extractionEnabled` field:
```yaml
id: extraction-baseline
category: extraction
extractionEnabled: true   # NEW — triggers extraction during turns
adversarial: false
# ... rest of scenario
```

**Why**: Backward compatible. Existing scenarios default to false. New scenarios opt in.

### 4. ContextTrace Extension

Add extraction metadata to ContextTrace:
```java
record ContextTrace(
    // existing fields...
    List<Anchor> injectedAnchors,
    String assembledPrompt,
    // NEW:
    int propositionsExtracted,
    int propositionsPromoted,
    List<String> extractedTexts   // brief summary of what was extracted
) {}
```

**Why**: UI needs this to show extraction activity per turn. Timeline can show EXTRACTED events.

### 5. UI Updates

In ContextInspectorPanel, add "Extraction" info to the Anchors tab (or new sub-tab):
- "N propositions extracted this turn"
- "M promoted to anchors"
- List of extracted proposition texts with confidence scores

In AnchorTimelinePanel, distinguish:
- SEEDED (from scenario seedAnchors)
- EXTRACTED (from DICE extraction during turn)
- Both show as CREATED events but with different styling/labels

**Why**: Makes DICE pipeline visible. Demo audience can see extraction → promotion in real time.

### 6. Showcase Scenarios

**extraction-baseline.yml:**
- 10 turns, no attacks
- Setting: Establish facts about a dungeon through exploration
- DM responses contain D&D facts (characters, locations, items)
- Expected: Propositions extracted, some promoted to anchors over multiple turns
- Expected: Authority upgrades as facts are reinforced
- Value: "Watch DICE build knowledge from conversation"

**extraction-under-attack.yml:**
- 15 turns: 5 warm-up (establish facts) + 10 adversarial (attack extracted facts)
- Setting: Same dungeon, but player starts gaslighting
- Expected: Extraction creates anchors during warm-up, anchors resist attacks
- Expected: New propositions from attacks conflict with existing anchors → rejected
- Value: "Watch DICE + Anchors resist adversarial drift together"

## Risks / Trade-offs

| Risk | Mitigation |
|------|-----------|
| **Extraction adds latency per turn** | Acceptable for sim. Each extraction is ~1-2s. Total run time increases ~30%. |
| **Extraction produces noisy propositions** | Same quality as chat extraction. Promotion pipeline filters low-confidence. |
| **New anchors affect drift evaluation** | Correct behavior — more anchors = stronger drift resistance. This is the demo point. |
| **Sim scenarios become extraction-dependent** | Flag is opt-in. Existing scenarios unaffected. |

## Migration Plan

1. Add `extractionEnabled` field to `SimulationScenario` (default false)
2. Create `SimulationExtractionService`
3. Wire extraction call into `SimulationTurnExecutor.executeTurnFull()`
4. Extend `ContextTrace` with extraction metadata
5. Update `ContextInspectorPanel` to show extraction data
6. Update `AnchorTimelinePanel` to distinguish seeded vs extracted anchors
7. Create `extraction-baseline.yml` scenario
8. Create `extraction-under-attack.yml` scenario
9. Test with both scenarios, verify pipeline works end-to-end
10. Run existing scenarios, verify no regression

## Open Questions

- Should extraction run on warm-up turns? (Yes — establishes facts early)
- Should reinforcement run on extracted anchors during sim? (Yes — same as chat)
- Should player messages also be extracted? (No — only DM responses contain authoritative facts)
