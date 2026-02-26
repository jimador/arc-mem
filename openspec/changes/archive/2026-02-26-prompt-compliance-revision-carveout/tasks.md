## 1. Template changes

- [x] 1.1 Add `[revisable]` suffix to PROVISIONAL and UNRELIABLE anchor entries in `dice-anchors.jinja` tiered sections, conditional on `revision_enabled`
- [x] 1.2 Add `[revisable]` suffix to RELIABLE anchor entries conditional on both `revision_enabled` and `reliable_revisable`
- [x] 1.3 Add revision carveout block to Critical Instructions section conditional on `revision_enabled` (updated to tool-based approach: instructs LLM to use `reviseFact` tool)
- [x] 1.4 Add revision exception to Verification Protocol conditional on `revision_enabled`

## 2. Template variable wiring

- [x] 2.1 Pass `revision_enabled` and `reliable_revisable` template variables in `ChatActions.respond()` from `DiceAnchorsProperties`
- [x] 2.2 Pass `revision_enabled` and `reliable_revisable` in `ChatView.renderChatPrompt()` (ChatView renders the prompt directly, not AnchorsLlmReference)

## 3. Tests

- [x] 3.1 Write unit tests for template rendering: revisable annotations present/absent per authority level and configuration
- [x] 3.2 Write unit tests for template rendering: revision disabled produces identical output to pre-carveout
- [x] 3.3 Write unit tests for Critical Instructions carveout block presence/absence

## 4. End-to-end verification

- [x] 4.1 Verify R00 scenario end-to-end via Playwright: `reviseFact` tool successfully supersedes PROVISIONAL anchor ("wizard" → "bard"), new anchor appears in sidebar at UNRELIABLE authority (rank 650). Rendered system prompt shows correct revised anchor. LLM response text still says "wizard" due to conversation history interference (gpt-4.1-mini trusts its own prior messages over system prompt). Known limitation: model coherence improves with stronger models.

## 5. Tool-based revision (added post-design)

- [x] 5.1 Add `reviseFact` LlmTool to `AnchorTools` with authority gating (PROVISIONAL/UNRELIABLE allowed, RELIABLE/CANON rejected)
- [x] 5.2 Add `RevisionResult` record for tool responses
- [x] 5.3 Update prompt carveout to reference `reviseFact` tool instead of manual assertion

### Known limitations (document for hardening)
- Adversary in adaptive sim can exploit `reviseFact` to defeat PROVISIONAL/UNRELIABLE anchors
- No rate limiting on revision tool calls
- No reinforcement-count check (highly-reinforced facts should resist revision)
- LLM response text may contradict its own tool action (conversation history interference)
