# Implementation Tasks

## 1. Create Inventory Document Structure

- [ ] 1.1 Create `docs/dev/embabel-api-inventory.md` with section headings: Current Usage, Available But Unused, Tool Organization, Recommended Patterns
- [ ] 1.2 Add introductory paragraph stating document purpose, Embabel Agent version (0.3.5-SNAPSHOT), and date of last verification

## 2. Document Current Usage

- [ ] 2.1 Document `@EmbabelComponent` usage with file:line reference (`ChatActions.java`)
- [ ] 2.2 Document `@Action` usage with parameters `trigger=UserMessage.class, canRerun=true` and file:line reference (`ChatActions.java`)
- [ ] 2.3 Document `@MatryoshkaTools` usage with parameters `name, description` and file:line references (`AnchorTools.java`, `AnchorRetrievalTools.java`)
- [ ] 2.4 Document `@LlmTool` usage with parameter `description` and file:line references (`AnchorTools.java`, `AnchorRetrievalTools.java`)
- [ ] 2.5 Document interfaces and APIs used: `ActionContext`, `AiBuilder`, `AgentProcessChatbot` (utility mode), `Chatbot`, `Conversation`, `ChatSession`, `Message` types
- [ ] 2.6 Document patterns: Jinja2 template rendering via `.rendering()`, tool registration via `.withToolObjects()`, Spring event listeners for DICE extraction
- [ ] 2.7 Verify all file:line references against current source (run grep to confirm annotation locations)

## 3. Document Available-But-Unused Capabilities

- [ ] 3.1 Document 8 unused annotation parameters:
  - [ ] 3.1.1 `@Action(cost)` -- planning cost for multi-action path selection
  - [ ] 3.1.2 `@Action(toolGroups)` -- declarative tool group binding per action
  - [ ] 3.1.3 `@MatryoshkaTools(removeOnInvoke)` -- progressive disclosure behavior
  - [ ] 3.1.4 `@MatryoshkaTools(categoryParameter)` -- category-based child tool selection
  - [ ] 3.1.5 `@MatryoshkaTools(childToolUsageNotes)` -- LLM guidance for child tools
  - [ ] 3.1.6 `@LlmTool(returnDirect)` -- bypass additional LLM processing of results
  - [ ] 3.1.7 `@LlmTool(category)` -- tool grouping within @MatryoshkaTools
  - [ ] 3.1.8 `@LlmTool.Param(description, required)` -- rich parameter descriptions for LLM
- [ ] 3.2 Document unused annotations: `@AchievesGoal`, `@Agent`, `@Export` with purpose and when valuable
- [ ] 3.3 Document unused modes: GOAP chatbot mode, blackboard binding system, message-vs-trigger pattern
- [ ] 3.4 For each entry, include a brief "when valuable" note explaining applicability to dice-anchors

## 4. Document Tool Organization Rationale

- [ ] 4.1 Explain CQS (Command-Query Separation) principle as applied to `@MatryoshkaTools`
- [ ] 4.2 Document distinction between query tools (read-only, safe for simulation) and command tools (mutations, chat-only)
- [ ] 4.3 Document how CQS enables selective registration in different execution contexts (simulation vs. chat)
- [ ] 4.4 Reference current tool classes (`AnchorTools`, `AnchorRetrievalTools`) as concrete examples

## 5. Document Recommended Patterns

- [ ] 5.1 Document tool description best practices (concise, action-oriented, include return type hints)
- [ ] 5.2 Document error handling in `@LlmTool` methods (return result records, not exceptions)
- [ ] 5.3 Document message-vs-trigger pattern (`UserMessage` vs. `ChatTrigger`) and when each applies
- [ ] 5.4 Document `@LlmTool.Param` usage pattern for parameter-level descriptions

## 6. Integrate into DEVELOPING.md

- [ ] 6.1 Add reference to `docs/dev/embabel-api-inventory.md` in DEVELOPING.md under an appropriate section
- [ ] 6.2 Verify the link resolves correctly from the repository root

## 7. Verification

- [ ] 7.1 Grep codebase for all 4 annotations; confirm every usage site is documented in Current Usage section
- [ ] 7.2 Confirm all file:line references point to correct locations
- [ ] 7.3 Confirm Available But Unused section covers all 8 parameters identified in R01 research
- [ ] 7.4 Confirm document is reachable from DEVELOPING.md
- [ ] 7.5 Read document end-to-end for clarity and completeness

## Definition of Done

- Inventory document exists at `docs/dev/embabel-api-inventory.md`
- All 4 used annotations documented with file:line references
- All 8 unused parameters documented with applicability context
- CQS tool restructuring rationale documented
- Recommended patterns section populated
- DEVELOPING.md links to inventory
- All file:line references verified against current source
