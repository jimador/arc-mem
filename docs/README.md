# ARC-Mem Documentation

Technical documentation for ARC-Mem (Activation-Ranked Context Memory).

For project setup, configuration, and running instructions, see the [root README](../README.md).

## Reading Paths

### New to the project?

1. [Architecture](architecture.md) — runtime topology, package map, data model, authority hierarchy
2. [Data Flows](data-flows.md) — six end-to-end sequence diagrams showing how the system works
3. [Promotion, Revision & Supersession](promotion-revision-supersession.md) — mutation semantics reference
4. [Status & Caveats](status-and-caveats.md) — what works, what's approximate, what's missing

### Evaluating the approach?

1. [Related Work & Research](related-work-and-research.md) — positioning vs MemGPT/Zep/Graphiti/RAG; research backlog
2. [Evaluation Protocol](evaluation.md) — conditions, metrics, drift model
3. [White Paper Outline](drafts/whitepaper-outline.md) — arXiv-style paper structure

### Working on the UI?

1. [UI Views](ui-views.md) — route map, state machines, panel listings
2. [Architecture](architecture.md) — backend context

### Considering contributing?

1. [Status & Caveats](status-and-caveats.md) — known issues and credibility blockers
2. [Related Work & Research](related-work-and-research.md) — research backlog and next tracks

## Subsystem Documentation

- [Attention Tracker](attention-tracker-architecture.md) — attention pressure signals and windowing

## Architecture Decision Records

- [ADR-001: Embabel Goal Modeling](adr/001-embabel-goal-modeling.md) — evaluated GOAP for promotion pipeline; deferred

## External Drafts

- [Blog Post](drafts/blog.md) — history
- [White Paper Outline](drafts/whitepaper-outline.md) — future paper
