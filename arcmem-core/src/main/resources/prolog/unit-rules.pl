% unit-rules.pl -- Contradiction and invariant rules for ARC-Mem Prolog integration.
% Loaded via PrologRuleLoader.INSTANCE.loadFromResource("prolog/unit-rules.pl").
% Implements deterministic pre-filter per Sleeping LLM (Guo et al., 2025).

% Negation pairs (symmetric) -- atoms quoted to match claim/4 output from ContextUnitPrologProjector
negation('alive', 'dead').    negation('dead', 'alive').
negation('present', 'absent'). negation('absent', 'present').
negation('true', 'false').    negation('false', 'true').
negation('open', 'closed').   negation('closed', 'open').

% Layer 1: Contradiction via negation (cheapest -- checked first)
contradicts(A, B) :-
    claim(A, S, P, O),
    claim(B, S, P, O2),
    negation(O, O2),
    A \= B.

% Incoming text conflict: existing unit contradicted by incoming
conflicts_with_incoming(ExistingId) :- contradicts(incoming, ExistingId).

% Authority floor violation
authority_violation(Id) :-
    unit(Id, Auth, _, _, _),
    authority_floor(Id, MinAuth),
    Auth < MinAuth.

% Eviction immunity violation
eviction_violation(Id) :-
    unit(Id, _, _, _, _),
    eviction_immune(Id).
