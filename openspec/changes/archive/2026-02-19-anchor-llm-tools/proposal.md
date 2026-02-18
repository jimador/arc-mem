## Why

The LLM (Bigby) has no way to interact with the anchor system during conversation. It can only passively receive injected facts. Exposing anchor operations as `@LlmTool` methods lets the LLM query ("What do we know about the Lich King?"), pin ("This fact is critical"), and manage its own knowledge. This showcases deep Embabel Agent integration — the LLM becomes an active participant in knowledge management, not just a consumer. This is the "wow factor" for the Embabel maintainer.

## What Changes

- Create `AnchorTools` record annotated with `@MatryoshkaTools` containing `@LlmTool` methods
- Expose operations: query anchors by text/subject, get anchor details, pin/unpin anchor
- Wire tools into `ChatActions` so the LLM can call them during conversation
- Tools return structured data (records) per coding style, not formatted strings
- No simulation changes; tools are chat-only

## Capabilities

### New Capabilities
- `anchor-llm-tools`: LLM-callable tools for querying and managing anchors during chat

### Modified Capabilities
(None — additive feature, no existing behavior changes)

## Impact

- **Files**: New `chat/AnchorTools.java` (@MatryoshkaTools record), updated `ChatActions.java` (wire tools)
- **APIs**: New @LlmTool methods exposed to LLM during chat
- **Config**: No new config
- **Dependencies**: Requires Embabel Agent @LlmTool/@MatryoshkaTools support (already in dependencies)
- **Value**: LLM actively manages knowledge — impressive Embabel Agent showcase

## Constitutional Alignment

- RFC 2119 keywords: Tools MUST return records, MUST NOT modify CANON anchors
- Coding style: @MatryoshkaTools record, structured data returns, constructor injection
- Authority invariant: Tools MUST NOT downgrade authority or auto-assign CANON
