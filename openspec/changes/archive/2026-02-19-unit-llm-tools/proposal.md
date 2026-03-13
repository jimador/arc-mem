## Why

The LLM (Bigby) has no way to interact with the context unit system during conversation. It can only passively receive injected facts. Exposing context unit operations as `@LlmTool` methods lets the LLM query ("What do we know about the Lich King?"), pin ("This fact is critical"), and manage its own knowledge. This showcases deep Embabel Agent integration — the LLM becomes an active participant in knowledge management, not just a consumer. This is the "wow factor" for the Embabel maintainer.

## What Changes

- Create `ContextTools` record annotated with `@MatryoshkaTools` containing `@LlmTool` methods
- Expose operations: query context units by text/subject, get context unit details, pin/unpin context unit
- Wire tools into `ChatActions` so the LLM can call them during conversation
- Tools return structured data (records) per coding style, not formatted strings
- No simulation changes; tools are chat-only

## Capabilities

### New Capabilities
- `unit-llm-tools`: LLM-callable tools for querying and managing context units during chat

### Modified Capabilities
(None — additive feature, no existing behavior changes)

## Impact

- **Files**: New `chat/ContextTools.java` (@MatryoshkaTools record), updated `ChatActions.java` (wire tools)
- **APIs**: New @LlmTool methods exposed to LLM during chat
- **Config**: No new config
- **Dependencies**: Requires Embabel Agent @LlmTool/@MatryoshkaTools support (already in dependencies)
- **Value**: LLM actively manages knowledge — impressive Embabel Agent showcase

## Constitutional Alignment

- RFC 2119 keywords: Tools MUST return records, MUST NOT modify CANON context units
- Coding style: @MatryoshkaTools record, structured data returns, constructor injection
- Authority invariant: Tools MUST NOT downgrade authority or auto-assign CANON
