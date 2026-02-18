package dev.dunnam.diceanchors.persistence;

import com.embabel.dice.proposition.PropositionStatus;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.drivine.annotation.NodeFragment;
import org.drivine.annotation.NodeId;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A proposition is a natural language statement with typed entity mentions.
 * Neo4j graph representation of Proposition from the Dice project.
 * <p>
 * Propositions are the system of record - all other representations
 * (Neo4j relationships, vector embeddings) derive from them.
 * <p>
 * Invariant A1: when rank > 0, authority must be non-null.
 * Invariant A2: rank is always in [100, 900] when non-zero.
 * Invariant A3: pinned propositions are immune to rank-based eviction.
 */
@NodeFragment(labels = {"Proposition"})
public class PropositionNode {

    @NodeId
    private String id;

    /**
     * The context in which this proposition is relevant
     */
    private String contextId;

    /**
     * The statement in natural language (e.g., "Jim is an expert in GOAP")
     */
    private String text;

    /**
     * LLM-generated certainty (0.0-1.0)
     */
    private double confidence;

    /**
     * Staleness rate (0.0-1.0). High decay = information becomes stale quickly
     */
    private double decay;

    /**
     * LLM explanation for why this was extracted
     */
    private @Nullable String reasoning;

    /**
     * Chunk IDs that support this proposition
     */
    private List<String> grounding;

    /**
     * When the proposition was first created
     */
    private Instant created;

    /**
     * When the proposition was last updated
     */
    private Instant revised;

    /**
     * Current lifecycle status
     */
    private PropositionStatus status;

    /**
     * Optional URI reference
     */
    private @Nullable String uri;

    /**
     * Source IDs that this proposition was derived from (for lineage tracking)
     */
    private List<String> sourceIds;

    /**
     * Vector embedding for similarity search
     */
    private @Nullable List<Double> embedding;

    // --- Anchor fields (rank > 0 means this proposition is an anchor) ---

    /**
     * Anchor rank in [100, 900], or 0 if this proposition is not an anchor.
     * Higher rank = higher priority in context assembly and eviction resistance.
     */
    private int rank;

    /**
     * Authority level of this anchor: PROVISIONAL, UNRELIABLE, RELIABLE, or CANON.
     * Null when rank == 0 (not an anchor).
     */
    private @Nullable String authority;

    /**
     * When true, this anchor is immune to rank-based eviction regardless of budget pressure.
     */
    private boolean pinned;

    /**
     * Decay strategy for this anchor: NONE, GRADUAL, or EPISODIC.
     * Null when rank == 0 (not an anchor).
     */
    private @Nullable String decayType;

    /**
     * Timestamp of the most recent reinforcement event.
     * Null if this anchor has never been reinforced.
     */
    private @Nullable Instant lastReinforced;

    /**
     * Total number of times this anchor has been reinforced.
     */
    private int reinforcementCount;

    @JsonCreator
    public PropositionNode(
            @JsonProperty("id") String id,
            @JsonProperty("contextId") String contextId,
            @JsonProperty("text") String text,
            @JsonProperty("confidence") double confidence,
            @JsonProperty("decay") double decay,
            @JsonProperty("reasoning") @Nullable String reasoning,
            @JsonProperty("grounding") List<String> grounding,
            @JsonProperty("created") Instant created,
            @JsonProperty("revised") Instant revised,
            @JsonProperty("status") PropositionStatus status,
            @JsonProperty("uri") @Nullable String uri,
            @JsonProperty("sourceIds") List<String> sourceIds,
            @JsonProperty("rank") int rank,
            @JsonProperty("authority") @Nullable String authority,
            @JsonProperty("pinned") boolean pinned,
            @JsonProperty("decayType") @Nullable String decayType,
            @JsonProperty("lastReinforced") @Nullable Instant lastReinforced,
            @JsonProperty("reinforcementCount") int reinforcementCount) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.contextId = contextId != null ? contextId : "default";
        this.text = text;
        this.confidence = confidence;
        this.decay = decay;
        this.reasoning = reasoning;
        this.grounding = grounding != null ? grounding : List.of();
        this.created = created != null ? created : Instant.now();
        this.revised = revised != null ? revised : Instant.now();
        this.status = status != null ? status : PropositionStatus.ACTIVE;
        this.uri = uri;
        this.sourceIds = sourceIds != null ? sourceIds : List.of();
        this.rank = rank;
        this.authority = authority;
        this.pinned = pinned;
        this.decayType = decayType;
        this.lastReinforced = lastReinforced;
        this.reinforcementCount = reinforcementCount;
    }

    /**
     * Convenience constructor for a plain proposition (not an anchor).
     */
    public PropositionNode(String id, String contextId, String text, double confidence, double decay,
                           @Nullable String reasoning, List<String> grounding, Instant created, Instant revised,
                           PropositionStatus status, @Nullable String uri, List<String> sourceIds) {
        this(id, contextId, text, confidence, decay, reasoning, grounding, created, revised,
             status, uri, sourceIds, 0, null, false, null, null, 0);
    }

    public PropositionNode(String text, double confidence) {
        this(UUID.randomUUID().toString(), "default", text, confidence, 0.0, null, List.of(),
             Instant.now(), Instant.now(), PropositionStatus.ACTIVE, null, List.of(),
             0, null, false, null, null, 0);
    }

    // --- Standard proposition getters and setters ---

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContextId() {
        return contextId;
    }

    public void setContextId(String contextId) {
        this.contextId = contextId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public double getDecay() {
        return decay;
    }

    public void setDecay(double decay) {
        this.decay = decay;
    }

    public @Nullable String getReasoning() {
        return reasoning;
    }

    public void setReasoning(@Nullable String reasoning) {
        this.reasoning = reasoning;
    }

    public List<String> getGrounding() {
        return grounding;
    }

    public void setGrounding(List<String> grounding) {
        this.grounding = grounding;
    }

    public Instant getCreated() {
        return created;
    }

    public void setCreated(Instant created) {
        this.created = created;
    }

    public Instant getRevised() {
        return revised;
    }

    public void setRevised(Instant revised) {
        this.revised = revised;
    }

    public PropositionStatus getStatus() {
        return status;
    }

    public void setStatus(PropositionStatus status) {
        this.status = status;
    }

    public @Nullable String getUri() {
        return uri;
    }

    public void setUri(@Nullable String uri) {
        this.uri = uri;
    }

    public List<String> getSourceIds() {
        return sourceIds;
    }

    public void setSourceIds(List<String> sourceIds) {
        this.sourceIds = sourceIds;
    }

    public @Nullable List<Double> getEmbedding() {
        return embedding;
    }

    public void setEmbedding(@Nullable List<Double> embedding) {
        this.embedding = embedding;
    }

    // --- Anchor getters and setters ---

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public @Nullable String getAuthority() {
        return authority;
    }

    public void setAuthority(@Nullable String authority) {
        this.authority = authority;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public @Nullable String getDecayType() {
        return decayType;
    }

    public void setDecayType(@Nullable String decayType) {
        this.decayType = decayType;
    }

    public @Nullable Instant getLastReinforced() {
        return lastReinforced;
    }

    public void setLastReinforced(@Nullable Instant lastReinforced) {
        this.lastReinforced = lastReinforced;
    }

    public int getReinforcementCount() {
        return reinforcementCount;
    }

    public void setReinforcementCount(int reinforcementCount) {
        this.reinforcementCount = reinforcementCount;
    }

    /**
     * Returns true if this proposition is currently promoted to anchor status.
     */
    public boolean isAnchor() {
        return rank > 0;
    }

    @Override
    public String toString() {
        return "PropositionNode{" +
               "id='" + id + '\'' +
               ", text='" + text + '\'' +
               ", confidence=" + confidence +
               ", status=" + status +
               ", rank=" + rank +
               ", authority='" + authority + '\'' +
               '}';
    }
}
