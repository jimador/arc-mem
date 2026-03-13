## Why

The current "Anchors" terminology obscures what the system actually does — maintain an activation-ranked working-memory layer for long-horizon LLM conversations. The name "anchor" implies static pinning, while the mechanism is dynamic: semantic units compete for limited working-set capacity based on activation scores influenced by recency, reinforcement, relevance, authority, and decay. Rebranding to **ARC-Mem (Activation-Ranked Context Memory)** aligns the vocabulary with the cognitive science metaphor (working memory with activation-based maintenance), makes the system self-describing to new readers, and broadens the framing beyond propositions to general semantic units (entities, events, constraints, goals, questions).

## What Changes

- **BREAKING**: Rename public-facing terminology from "Anchor(s)" to "ARC-Mem" / "semantic unit(s)" / "activation score" across documentation, blog posts, whitepaper, and architecture descriptions
- **BREAKING**: Rename key Java interfaces and classes where they form the public API surface (e.g., `AnchorEngine` → `ArcMemEngine`, `Anchor` → `SemanticUnit` or similar) — exact renames to be determined in design phase
- Update all documentation (CLAUDE.md, README, blog, whitepaper outline, coding-style) to use ARC-Mem terminology
- Update architecture descriptions and diagrams to reflect the pipeline: **Conversation → Semantic Unit Extraction (DICE) → ARC-Mem (Activation-Ranked Context Memory) → Structured Prompt Context → LLM Reasoning**
- Reframe "rank" as "activation score" in documentation and comments (internal field name MAY remain `rank` for backward compatibility with Neo4j schema)
- Reframe "budget enforcement" as "working-memory capacity" in user-facing docs
- Broaden documentation language from "propositions" to "semantic units" to reflect that ARC-Mem operates over propositions, entities, events, constraints, goals, and questions
- Clarify ARC-Mem's position as a **protected prompt-level working-memory layer** between volatile conversation context and longer-term semantic/archival memory
- Update OpenSpec spec names and descriptions that reference "anchor" terminology
- Update prompt templates that reference anchors in user/system-facing language
- Update simulation scenario descriptions and UI labels

## Capabilities

### New Capabilities

- `arc-mem-terminology`: Defines the canonical ARC-Mem terminology mapping (old term → new term) and governs consistent usage across all documentation, code comments, UI labels, and prompt templates. This is the single source of truth for the rebrand vocabulary.

### Modified Capabilities

- `unit-lifecycle`: Reframe lifecycle documentation around semantic unit activation/deactivation rather than anchor creation/eviction
- `unit-assembly`: Reframe as ARC-Mem context assembly — activation-ranked semantic units assembled into structured prompt context
- `unit-trust`: Reframe trust pipeline documentation around semantic unit authority and activation signals
- `unit-conflict`: Reframe conflict detection/resolution around semantic unit mediation
- `unit-maintenance-strategy`: Reframe maintenance as working-memory housekeeping (activation refresh, consolidation, pruning)
- `unit-extraction`: Reframe as semantic unit extraction — the DICE → ARC-Mem intake pipeline
- `memory-tiering`: Reframe tiers in terms of activation levels (hot/warm/cold → high/medium/low activation)
- `prompt-token-budget`: Reframe as working-memory capacity management
- `compaction`: Reframe as working-memory compaction for capacity-exceeded scenarios
- `drift-evaluation`: Update terminology in drift evaluation prompts and scoring
- `benchmark-report`: Update report language and labels to ARC-Mem terminology
- `resilience-report`: Update resilience scoring language to ARC-Mem terminology

## Impact

- **Code**: All files in `anchor/`, `assembly/`, `extract/`, `chat/`, `sim/`, `prompt/` packages — class renames, Javadoc updates, comment updates, variable names in user-facing contexts
- **Neo4j schema**: Node labels and relationship types MAY need migration scripts if renamed (to be assessed in design — may keep internal names for backward compat)
- **Prompt templates**: All files in `src/main/resources/prompts/` that reference "anchor" in LLM-facing text
- **Configuration**: Property names in `application.yml` under `dice-anchors.*` namespace
- **Documentation**: CLAUDE.md, blog.md, whitepaper-outline.md, coding-style.md, all OpenSpec specs
- **UI**: Vaadin views — labels, tooltips, and descriptions referencing anchors
- **Tests**: Test class names, display names, and assertion messages
- **Docker/CI**: No impact expected (infrastructure is terminology-neutral)
- **DICE dependency**: No changes to DICE itself — ARC-Mem consumes DICE output (propositions) but the extraction interface remains stable
