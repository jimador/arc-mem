# Long-Running LLM Conversations Need Working Memory, Not Just More Context

A few months ago I ran into a problem while trying to use LLMs for D&D campaign management.

I had a couple of campaigns going with friends, and I wanted an excuse to do something more serious with agentic frameworks than another toy demo. Campaign management seemed like a natural fit. Behind the fun there is structured work: characters, NPCs, items, monsters, encounters, locations, motivations, relationships, plot hooks, and world state. It is creative work, but it is also constrained work.

That is the kind of space where LLMs should be useful. They are good at improvisation and synthesis. I wanted an assistant that could help with session prep, NPC continuity, and worldbuilding without forcing a group of adults to maintain perfect notes between work, family, and everything else.

I built the first version on [Embabel](https://embabel.com), using markdown notes plus retrieval. In theory, that should have been enough. In practice, after a while, the assistant would lose the plot. NPC motivations drifted, character details mutated, and facts that had been settled earlier stopped constraining later turns. Sometimes the model contradicted prior state outright. More often, it just started acting like those earlier parts of the conversation were not important anymore.

That was the interesting failure. It was not just "the model forgot a detail." The conversation no longer had a stable sense of what mattered.

This was not quite hallucination in the narrow sense, and it was not really a guardrails problem either. I didn't want to lock the system down. The free-form flow was part of the point. I wanted the assistant to stay creative and conversational without losing track of things that had already been established. In practice, the problem looked like:

* established facts quietly losing force
* later turns overriding earlier state without enough justification
* plausible reframes being treated like legitimate updates
* contradiction, revision, uncertainty, and normal world progression getting blurred together

The last point matters because real conversations contain rules, exceptions, scope boundaries, and context-dependent rulings that have to coexist. Humans handle that nuance almost automatically. LLMs are much more likely to flatten it as the conversation goes on.

## The Real Problem

My experience lined up with a broader pattern. In [*LLMs Get Lost in Multi-Turn Conversation*](https://arxiv.org/abs/2505.06120), Laban et al. report substantial degradation from single-turn to multi-turn settings, along with a large increase in unreliability between runs. The implication is that the problem is not simply context-window size. Multi-turn instability shows up even when raw context capacity is not the binding constraint.

Tabletop RPGs made the failure easy to see. If a character is a wizard, that should constrain spells, gear, enemies, and story beats. If an NPC is dead, they should stay dead unless the story explicitly changes that. If the party already established which crystal opens a door, later turns should not casually reinterpret it because the conversation moved on.

But this is not a niche problem. It is the same failure mode you would care about in any assistant that has to stay adaptive without becoming ungrounded:

* medical constraints that must not drift
* legal facts that should not be casually reinterpreted
* operational knowledge that must remain stable across long troubleshooting sessions
* agent state that should stay consistent under sustained tool use and conversation pressure

## Why Standard Memory Approaches Fall Short

I tried the usual moves: larger context windows, RAG, memory stores, and tool-backed flows. They helped, but mostly by confirming the same thing the earlier paper pointed to: this was an underlying problem with long-running LLM conversations, not just a missing piece of plumbing.

Bigger context windows increased capacity, but more tokens did not reliably translate into better attention allocation. Markdown notes worked as external memory, but not continuity inside the conversation itself. Retrieval helped surface relevant facts, but those facts still had to compete with everything else in the prompt.

That limitation mattered. Traditional RAG, especially when backed by vector search, is built around semantic similarity. It is good at finding things that look like the current context. But the problem I was running into was not always about similarity. Sometimes the most important fact was not the most semantically similar fact. Sometimes it was just the one that still should have mattered.

Once semantic-similarity search starts to show its limits, a knowledge graph is the next natural step. That led me to [DICE](https://github.com/embabel/dice), which was a natural fit. DICE supports proposition extraction, grounding, and graph persistence. If I wanted the system to know what entities exist, what relationships hold, and what propositions had already been extracted, DICE was the right substrate.

And it helped. But it did not solve the core problem.

A knowledge graph can tell you what facts exist for a given context. Retrieval can tell you what might be relevant. Neither one, by itself, tells the system which facts should matter most in a given moment, which ones should stay salient, which ones should be harder to contradict, or which updates should be treated as untrusted. That is the gap I kept running into.

You can see adjacent systems circling the same space from different directions. [MemGPT](https://arxiv.org/abs/2310.08560) treats memory as a systems problem and focuses on paging information in and out of context. [Graphiti](https://arxiv.org/abs/2501.13956) is especially interesting for temporal knowledge and invalidation of prior facts. Both are useful, but neither fully answers the question I cared about most: not just what the system can recover, but what should continue to carry weight inside an ongoing conversation.

## The Missing Layer: Governed Working Memory

What I actually needed was not just a better way to store or retrieve facts. I needed a way to keep certain facts explicitly in play as the conversation evolved.

The raw context window is a kind of working memory, but it is volatile working memory. It gets crowded, compacted, summarized, and reweighted every turn. What I was missing was a bounded, governed working set: facts that stay active, stay visible to the model, and stay harder to overwrite even as the rest of the conversation shifts.

Once you look at it that way, the shape of the solution becomes clearer: you need a bounded set of facts that stays in play, distinguishes trusted constraints from tentative claims, handles contradiction and revision differently, and remains present even as the rest of the conversation shifts.

This is not a replacement for archival recall. It sits on top of it. Facts can fall out of the active set and later come back into play when the conversation makes them relevant again. The job of governed working memory is to decide which facts should stay active now, which ones can fade, and how they re-enter the conversation when they matter again.

## Working Memory in Practice

My current proof of concept for this is [**Anchors**](https://github.com/jimador/dice-anchors).

DICE handles proposition extraction and persistence. Embabel handles orchestration. Anchors sits on top of that and governs which extracted propositions are promoted into active working memory and how they persist there over time.

In this model, an anchor is a DICE proposition that has been promoted and given additional structure. The key fields are rank, authority, and membership in a bounded working set. In practice, that means the system maintains a small active pool of facts rather than feeding everything it has ever seen back into the prompt.

At runtime, anchors are injected into prompts as a ranked established-facts block. That is an important design choice. Retrieval-based memory is opportunistic: the system may surface the right fact, or it may not. Anchors, by contrast, are deliberately kept in play. They are explicitly marked and budgeted as part of prompt assembly itself.

The more interesting part is how that active set is managed.

Conflict checks evaluate contradictory candidate updates before they are allowed to quietly replace established state. Trust scoring helps determine whether a newly extracted proposition should be promoted at all. Reinforcement and decay adjust importance over time so repeatedly confirmed facts gain staying power while stale facts lose it. Budget enforcement keeps the working set bounded by evicting low-value anchors instead of allowing context to sprawl indefinitely.

Authority matters here as well. Some facts should remain easy to revise. Others should be harder to contradict or demote because they come from stronger provenance or have been repeatedly reinforced. That does not make the system infallible, but it does provide a principled way to distinguish between a tentative claim, a reliable constraint, and something that should behave more like a rule than a suggestion.

At the top of that hierarchy are CANON facts. Those are not just memories with a higher score. They can also act as compliance gates when they are injected into the prompt and checked during response handling. If a player tries to contradict established world state, slip in a deceptive claim, or keep attacking the same proposition until it erodes, those canon facts give the system a way to steer the response back toward what has already been established instead of treating the attack as just another turn. The same pattern shows up well outside games. In a long-running operations or support conversation, if the assistant has already established that a production system is in read-only incident mode or that an account is under fraud review, a later turn should not be able to casually override that constraint just because the conversation has moved on.

I also paired the implementation with a simulation and benchmarking harness so I could evaluate long-horizon drift under pressure rather than relying on intuition alone. The harness runs turn-by-turn scenarios, injects adversarial or displacement pressure, and scores whether established facts are confirmed, contradicted, or simply omitted. That makes it possible to inspect failures at the fact level and compare memory conditions in a more disciplined way.

## Key Takeaways

The takeaway for me is simple: long-running conversations need more than storage and retrieval. They need some way to maintain, rank, and evolve the facts that are supposed to matter.

That is why I think working memory deserves to be treated as an explicit layer in agent architecture. Not just as whatever happens to be in the context window right now, and not just as a long-term memory store sitting off to the side, but as governed state with rules for promotion, trust, persistence, revision, and enforcement.

In this design, some of that governance shows up as canon facts that act less like passive memory and more like active guidance. They can help the model return to established world state, call out deception, and resist repeated attempts to erode a proposition that is supposed to stay load-bearing.

What makes this especially interesting is that it also turns into a monitoring problem, not just a memory problem. Once facts have explicit status and guardrails, you can measure which propositions are being attacked, which ones erode under pressure, and where the system fails to enforce the right constraint.

There is still more work to do on evidence gathering, ablations, and harder evaluation. But that combination of memory, policy, and observability feels like a promising area for continued work.