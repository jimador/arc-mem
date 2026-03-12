% anchor-rules.pl -- Contradiction and invariant rules for dice-anchors Prolog integration.
% Loaded via PrologRuleLoader.INSTANCE.loadFromResource("prolog/anchor-rules.pl").
% Implements deterministic pre-filter per Sleeping LLM (Guo et al., 2025).

% Negation pairs (symmetric) -- atoms quoted to match claim/4 output from AnchorPrologProjector
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

% Incoming text conflict: existing anchor contradicted by incoming
conflicts_with_incoming(ExistingId) :- contradicts(incoming, ExistingId).

% Authority floor violation
authority_violation(Id) :-
    anchor(Id, Auth, _, _, _),
    authority_floor(Id, MinAuth),
    Auth < MinAuth.

% Eviction immunity violation
eviction_violation(Id) :-
    anchor(Id, _, _, _, _),
    eviction_immune(Id).
