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

You can see adjacent systems circling the same space from different directions. [MemGPT](https://arxiv.org/abs/2310.08560) treats memory as a systems problem and focuses on paging information in and out of context. [Graphiti](https://arxiv.org/abs/2501.13956) is especially interesting for temporal knowledge and invalidation of prior facts. [Cognitive Workspace](https://arxiv.org/abs/2508.13171) pushes toward active memory management in the reasoning loop itself. [HiAgent](https://arxiv.org/abs/2408.09559) explores hierarchical working memory for long-horizon agent tasks. Storage-oriented systems such as [Mem0](https://arxiv.org/abs/2504.19413) and [A-MEM](https://arxiv.org/abs/2502.12110) improve extraction and persistence. All of that is useful, but it still leaves the question I cared about most: not just what the system can recover, but what should continue to carry weight inside an ongoing conversation.

## The Missing Layer: Governed Working Memory

The problem was not whether the system knew something somewhere. The problem was whether that thing was still active enough to shape the next turn.

That is where the raw context window starts to break down. It already acts like a kind of working memory, but it is volatile. Every turn adds more tokens, more pressure, more chances for summarization, compaction, reframing, and distraction. Facts that should still be constraining the conversation do not just stay active on their own. They get diluted by everything else going on.

Once I started looking at it that way, the architecture got a lot simpler in my head. Recent turns are volatile context. A graph like DICE is closer to structured semantic memory. Documents, notes, and logs are archival memory. What was missing was the layer in between: a bounded working set that stays active in the prompt and keeps load-bearing state from quietly dropping out just because the conversation wandered.

That is what I mean by governed working memory.

The concrete mechanism I have been using for that layer is ARC, short for Activation-Ranked Context. The idea borrows from the [ACT-R](https://act-r.psy.cmu.edu/) cognitive architecture, specifically the notion that memory chunks have an activation level that determines retrieval probability — items that are used frequently and recently stay accessible, while everything else fades. The basic idea is that facts, claims, events, or whatever semantic unit you want to work with should not stay active just because they were stored somewhere. They should stay active because they are still earning it. Maybe they were mentioned recently. Maybe they have been reinforced over several turns. Maybe they are highly relevant to the current exchange. Maybe they come from a more trusted source. Maybe they are under contradiction pressure and need to stay visible for that reason.

If those signals weaken, the unit can fade out of the active set. If the conversation comes back around, it can come back in. The point is not just to remember things somewhere in the system. The point is to keep the right things active in the part of the system that is actually shaping the next response.

ARC does not replace retrieval or long-term storage. It sits between them and the prompt. Its job is to keep the few things that still matter from having to win a brand new relevance contest every turn.

## ARC in Practice

ARC is the mechanism. [**ARC-Mem**](https://github.com/jimador/dice-anchors) is my current implementation of it, built on DICE for proposition extraction and Embabel for agent orchestration.

A memory unit is a DICE proposition that has been promoted and given additional structure: an activation score, an authority level, and membership in a bounded working set. In practice, the system maintains a small active pool of facts — currently capped at 20 — rather than feeding everything it has ever seen back into the prompt. That working set is injected as a protected block before the model reasons over the next turn. Retrieval-based memory is opportunistic; ARC is deliberate. If a fact is in the working set, it is there every turn until something explicitly displaces it.

The more interesting part is what happens when the working set is under pressure.

Say the party enters a dungeon and the DM establishes that a guardian protects the east gate. DICE extracts the proposition, the trust pipeline evaluates it, and if it passes, it gets promoted into the active set. A few turns later, a player casually claims "there's no guardian, the gate is open." That is not just new information — it contradicts an established fact. Before the incoming claim can quietly overwrite the guardian, the conflict detector catches it and the resolver decides what to do. If the existing fact has higher authority, the incoming claim gets rejected. If the claim is more credible, the old fact gets archived with a supersession record. Either way, it is an explicit decision, not silent drift.

Trust scoring gates the front door. Not every extracted proposition earns promotion. Source authority, extraction confidence, and reinforcement history all factor in. A fact mentioned once by a player gets lower trust than something the DM established and the conversation reinforced three times. That difference matters because it determines how hard a fact is to displace later.

Reinforcement and decay keep the working set honest over time. Facts that keep coming up earn higher activation scores and may climb the authority ladder. Facts that stop being relevant decay and eventually get evicted to make room. Budget enforcement handles the rest — when the cap is hit, the lowest-ranked non-pinned unit gets dropped.

At the top of the authority hierarchy are CANON facts. Those are not just memories with a higher score. They can act as compliance gates — checked during response handling so the model steers back toward established state rather than treating a contradiction as just another turn. If a player keeps attacking the same proposition until it erodes, canon facts give the system a structural reason to hold the line. The same pattern shows up outside games. In a long-running support conversation, if the assistant has established that a production system is in read-only incident mode, a later turn should not be able to casually override that just because the conversation moved on.

I also paired the implementation with a simulation and benchmarking harness so I could evaluate long-horizon drift under pressure rather than relying on intuition alone. The harness runs turn-by-turn scenarios, injects adversarial or displacement pressure, and scores whether established facts are confirmed, contradicted, or simply omitted. That makes it possible to inspect failures at the fact level and compare memory conditions in a more disciplined way.

## Key Takeaways

The takeaway for me is simple: long-running conversations need more than storage and retrieval. They need some way to maintain, rank, and evolve the facts that are supposed to matter.

That is why I think working memory deserves to be treated as an explicit layer in agent architecture. Not just as whatever happens to be in the context window right now, and not just as a long-term memory store sitting off to the side, but as governed state with rules for promotion, trust, persistence, revision, and enforcement.

In this design, some of that governance shows up as canon facts that act less like passive memory and more like active guidance. They can help the model return to established world state, call out deception, and resist repeated attempts to erode a proposition that is supposed to stay load-bearing.

What makes this especially interesting is that it also turns into a monitoring problem, not just a memory problem. Once facts have explicit status and guardrails, you can measure which propositions are being attacked, which ones erode under pressure, and where the system fails to enforce the right constraint.

There is still more work to do on evidence gathering, ablations, and harder evaluation. But that combination of memory, policy, and observability feels like a promising area for continued work.
