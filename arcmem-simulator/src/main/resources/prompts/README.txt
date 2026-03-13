# Prompt Templates

All LLM prompts in this project are Jinja2 templates (.jinja files).
Loaded and rendered via PromptTemplates.load(path) + PromptTemplates.render(path, variables).

## Root prompts/ (chat and shared)
- units-reference.jinja — Renders memory units by authority tier (STRICT/MODERATE/WEAK) with RFC 2119 compliance language
- units-reference-overhead.jinja — Compact unit reference for token-budget-constrained scenarios
- arc-mem.jinja — Main system prompt for chat flow: integrates DICE extraction and ARC-Mem compliance framework
- propositions-reference.jinja — Reference block for extracted propositions (intermediate DICE output)
- drift-evaluation-system.jinja — System prompt for LLM-based drift evaluation
- drift-evaluation-system.txt — Alternative plaintext drift evaluation template

## prompts/sim/ (simulation engine)
- system.jinja — D&D Dungeon Master role definition and ARC-Mem compliance instructions
- user.jinja — Player turn context and ARC-Mem compliance checking framework
- adversary.jinja — Base adversary prompt for baseline (non-adaptive) attacks
- adversarial-request.jinja — Generic adversarial prompt template
- adversarial-fallback-message.jinja — Fallback message when adversary generation fails
- warmup-player-message.jinja — Generates player messages during warm-up phase (establishing facts)
- default-player-message.jinja — Generates player messages during establish phase (baseline attacks)
- evaluator.jinja — LLM prompt for evaluating DM response drift against memory units
- drift-evaluation-user.jinja — User-facing drift evaluation prompt
- conversation-line.jinja — Formats individual conversation turn lines for context
- summary.jinja — Generates end-of-simulation summary and statistics

## prompts/dice/ (DICE proposition pipeline)
- extract_dnd_propositions.jinja — Extracts propositions from D&D conversation via DICE
- duplicate-system.jinja — Detects duplicate propositions (system prompt)
- duplicate-user.jinja — Detects duplicate propositions (user prompt)
- batch-duplicate-system.jinja — Batch duplicate detection (system prompt)
- batch-duplicate-user.jinja — Batch duplicate detection (user prompt)
- conflict-detection.jinja — Detects contradictions between propositions
- batch-conflict-detection.jinja — Batch conflict detection
- batch-trust-scoring.jinja — Scores proposition trust/authority

## Usage Pattern
  // Load and render:
  var template = PromptTemplates.load("prompts/sim/adversarial-request.jinja");
  var rendered = PromptTemplates.render("prompts/sim/adversarial-request.jinja", Map.of("key", value));

## Adding a New Template
1. Create the .jinja file in the appropriate subdirectory.
2. Reference the path as a string constant (see PromptPathConstants when available).
3. Call PromptTemplates.render() with a Map of variables matching the template's {{ var }} placeholders.
