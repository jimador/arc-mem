# dice-anchors Project Overview

## Purpose
dice-anchors is a standalone demo app demonstrating how **Anchors** — enriched DICE Propositions with rank, authority, and budget management — resist adversarial prompt drift. It serves as a working reference for DICE <-> Anchor integration.

## Tech Stack
- Java 21 / Spring Boot 3.5.10 / Embabel Agent 0.3.5-SNAPSHOT / DICE 0.1.0-SNAPSHOT
- Drivine 0.0.14 (Neo4j ORM) / Neo4j 5.x / Vaadin 24.6.4
- Single-module Maven project

## Architecture
- Two Vaadin routes: `/` (SimulationView) and `/chat` (ChatView)
- Neo4j is the sole persistence store (no PostgreSQL)
- Anchors are propositions with rank > 0 (no separate node type)
- Simulation harness runs YAML-defined adversarial/baseline scenarios
- Chat interface uses Embabel Agent for LLM orchestration with anchor context injection

## Key Subsystems
- **anchor/** — Engine, policies (decay, reinforcement), conflict detection/resolution
- **persistence/** — Neo4j persistence via Drivine (AnchorRepository, PropositionNode)
- **assembly/** — Context injection (AnchorsLlmReference, AnchorContextLock)
- **extract/** — DICE -> Anchor promotion (AnchorPromoter, DuplicateDetector)
- **chat/** — Embabel chat integration (ChatActions, ChatView)
- **sim/** — Simulation harness (SimulationService, SimulationTurnExecutor, SimulationView)
- **domain/** — D&D entity types (Character, DndLocation, DndItem, Faction, Creature, StoryEvent)
