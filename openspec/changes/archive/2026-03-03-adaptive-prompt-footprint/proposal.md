## Why

All anchors currently use the same prompt template regardless of authority level. A CANON anchor that has been reinforced dozens of times gets the same verbose treatment as a freshly promoted PROVISIONAL anchor. This wastes token budget on well-established knowledge, leaving less room for newer propositions that need maximum detail to be respected by the LLM. Research from sleeping-llm (Guo et al., 2025) demonstrates that consolidated knowledge can reduce its injection footprint as it stabilizes -- MEMIT scale factors decrease from 1.0 to 0.5 to 0.1 to 0.0 as LoRA absorbs the knowledge. This directly parallels authority-graduated prompt verbosity: higher-authority anchors need less verbose reminding while lower-authority propositions need full context.

## What Changes

- Add 4 authority-graduated prompt template files (PROVISIONAL: full detail, UNRELIABLE: text + brief context, RELIABLE: condensed assertion, CANON: minimal reference)
- Update `PromptPathConstants` with constants for the new template files
- Modify `AnchorsLlmReference` to select per-anchor templates by authority when adaptive footprint is enabled (opt-in)
- Update `PromptBudgetEnforcer` token estimation to use authority-dependent template sizes instead of a uniform estimate
- Add `adaptiveFootprintEnabled` toggle to `DiceAnchorsProperties.AssemblyConfig` (default: false)
- When disabled, behavior is identical to current uniform templates (backward compatible)

## Capabilities

### New Capabilities
- `adaptive-prompt-footprint`: Authority-graduated prompt templates with per-tier verbosity levels, template selection logic in prompt assembly, and authority-aware token estimation in budget enforcement

### Modified Capabilities
- `prompt-token-budget`: Token estimation in `PromptBudgetEnforcer` changes to account for authority-dependent template sizes when adaptive footprint is enabled

## Impact

- **Files**: New template files in `src/main/resources/prompts/` (4 files). Modified `PromptPathConstants`, `AnchorsLlmReference`, `PromptBudgetEnforcer`, `DiceAnchorsProperties`
- **Config**: New `dice-anchors.assembly.adaptive-footprint-enabled` property (boolean, default false)
- **Prompts**: Per-authority template files replace the uniform `anchors-reference.jinja` rendering when enabled
- **Behavior**: When enabled, CANON anchors use minimal templates (~30% of current tokens), PROVISIONAL anchors use full templates. When disabled, no change.
- **Testing**: New unit tests for template selection and authority-aware estimation. Existing tests unaffected (default off).

## Constitutional Alignment

- **Article I (RFC 2119)**: All spec requirements use RFC 2119 keywords
- **Article III (Constructor injection)**: No new beans; changes are within existing classes
- **Article IV (Records)**: Config uses existing record pattern in `DiceAnchorsProperties`
- **Article V (Anchor invariants)**: No changes to anchor rank, budget, or authority semantics. Template verbosity is a prompt-level concern only
- **Article VII (Test-first)**: Tests for template selection and budget estimation accompany implementation

## Research Attribution

Sleeping-LLM MEMIT scale-down schedule (Guo et al., 2025): "MEMIT scale factors decrease from 1.0 to 0.5 to 0.1 to 0.0 as LoRA absorbs the knowledge, directly paralleling authority-graduated prompt verbosity." Prompt-based systems lack true internalization (each call is a new context window), but the graduated reduction principle applies to token budget optimization.
