# Research Task: ACT-R + LLM Literature Positioning

## Task ID

`R01`

## Target Features

F01 (Whitepaper Outline Refinement)

## Research Question

How do recent ACT-R + LLM papers position relative to ARC's governed working memory claims? Are they complementary, competitive, or orthogonal?

## Channels

- web (arXiv, ACM, Frontiers)
- repo-docs (`docs/related-work-and-research.md` TODO section)

## Timebox

45 minutes

## Success Criteria

All 4 papers from the related-work TODO classified with:
1. Relationship to ARC (complementary/competitive/orthogonal)
2. 1-line positioning statement suitable for Section 3.2 of the whitepaper
3. Citation-ready reference

## Known Papers to Evaluate

### 1. LLM-ACTR / Cognitive LLMs (Wu et al., 2024–2025)
- **arXiv**: 2408.09176
- **Core idea**: Transfer knowledge from ACT-R cognitive models to LLMs via latent neural representations injected into adapter layers for fine-tuning
- **Domain**: Manufacturing decision-making (Design for Manufacturing)
- **Preliminary classification**: Orthogonal — fine-tunes LLM weights using ACT-R knowledge; ARC operates at prompt level without weight modification
- **Key distinction**: LLM-ACTR embeds cognitive patterns INTO the model; ARC maintains cognitive-inspired structures OUTSIDE the model in a prompt-level working memory buffer

### 2. Human-Like Remembering and Forgetting in LLM Agents (2026, ACM)
- **Core idea**: Integrates ACT-R memory dynamics (declarative retrieval, activation-based forgetting, interference) directly into LLM agent memory
- **Preliminary classification**: Complementary — uses ACT-R activation/decay for similar goals (salience management) but focuses on persistent agent episodic/semantic memory, not bounded prompt-level working memory
- **Key distinction**: Focuses on long-term memory retrieval dynamics; ARC focuses on short-term working memory retention and governance within the active prompt

### 3. Integrating Language Model Embeddings into ACT-R (Meghdadi et al., 2026, Frontiers)
- **Core idea**: Replaces hand-coded ACT-R associations with LLM-derived embeddings for spreading activation
- **Preliminary classification**: Orthogonal — enhances ACT-R with LLMs (direction opposite to ARC); more "ACT-R enhanced by LLMs" than "LLMs governed by ACT-R principles"
- **Key distinction**: Improves cognitive model fidelity; does not address LLM prompt-level memory governance

### 4. Prompt-Enhanced ACT-R and Soar Model Development (Wu et al., 2023–2025, AAAI Fall Symposium)
- **Core idea**: Uses LLMs as interactive interfaces to build and refine ACT-R/Soar production rules
- **Preliminary classification**: Orthogonal — LLMs as tools for cognitive model development, not cognitive architecture applied to LLM memory
- **Key distinction**: Tooling for cognitive science; no overlap with prompt-level memory governance

## Additional Paper to Evaluate

### 5. RYS: Layer Duplication in Transformers (Ng, 2025)
- **URL**: https://dnhkng.github.io/posts/rys/
- **Core idea**: Duplicating specific transformer layers improves model performance by providing "a second pass" through reasoning circuits
- **Preliminary classification**: Tangential — addresses architectural depth of reasoning, not working memory governance. Could be cited as evidence that transformer architecture constrains reasoning depth, motivating external memory mechanisms, but the connection is indirect.
- **Recommendation**: Brief mention in Section 3 discussion if space permits, not a primary related work citation

## Whitepaper Section 3.2 Positioning Template

For each paper, the outline should include a positioning statement following this pattern:

> [Author] et al. [verb] ACT-R [direction] by [mechanism]. This differs from ARC's approach because [key distinction]. [Complementary/orthogonal/competitive] to governed working memory.

## Open Questions

1. Are there additional ACT-R + LLM papers published in 2025-2026 not captured in the TODO list?
2. Does the "Cognitive Workspace" paper (arXiv:2508.13171) already cited in the outline overlap with any of these?

## Evidence Quality Notes

- Papers 1, 3, 4 are well-established (published venues, arXiv with versions)
- Paper 2 (ACM 2026) should be verified for publication status
- RYS is a blog post, not peer-reviewed — cite cautiously if at all
