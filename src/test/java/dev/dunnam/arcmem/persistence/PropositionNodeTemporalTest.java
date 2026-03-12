package dev.dunnam.diceanchors.persistence;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PropositionNode temporal field serialization")
class PropositionNodeTemporalTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Nested
    @DisplayName("JSON round-trip with temporal fields")
    class JsonRoundTrip {

        @Test
        @DisplayName("temporal fields survive JSON round-trip")
        void temporalFieldsSurviveJsonRoundTrip() throws Exception {
            var validFrom = Instant.parse("2026-01-01T00:00:00Z");
            var validTo = Instant.parse("2026-06-01T00:00:00Z");
            var txStart = Instant.parse("2026-01-01T00:00:00Z");
            var txEnd = Instant.parse("2026-06-01T12:00:00Z");
            var supersededBy = "successor-123";
            var supersedes = "predecessor-456";

            var node = new PropositionNode("test text", 0.9);
            node.setValidFrom(validFrom);
            node.setValidTo(validTo);
            node.setTransactionStart(txStart);
            node.setTransactionEnd(txEnd);
            node.setSupersededBy(supersededBy);
            node.setSupersedes(supersedes);

            var json = objectMapper.writeValueAsString(node);
            var deserialized = objectMapper.readValue(json, PropositionNode.class);

            assertThat(deserialized.getValidFrom()).isEqualTo(validFrom);
            assertThat(deserialized.getValidTo()).isEqualTo(validTo);
            assertThat(deserialized.getTransactionStart()).isEqualTo(txStart);
            assertThat(deserialized.getTransactionEnd()).isEqualTo(txEnd);
            assertThat(deserialized.getSupersededBy()).isEqualTo(supersededBy);
            assertThat(deserialized.getSupersedes()).isEqualTo(supersedes);
        }

        @Test
        @DisplayName("non-temporal fields preserved after round-trip")
        void nonTemporalFieldsPreservedAfterRoundTrip() throws Exception {
            var node = new PropositionNode("round-trip text", 0.85);
            node.setRank(500);
            node.setAuthority("RELIABLE");
            node.setValidFrom(Instant.now());

            var json = objectMapper.writeValueAsString(node);
            var deserialized = objectMapper.readValue(json, PropositionNode.class);

            assertThat(deserialized.getText()).isEqualTo("round-trip text");
            assertThat(deserialized.getConfidence()).isEqualTo(0.85);
            assertThat(deserialized.getRank()).isEqualTo(500);
            assertThat(deserialized.getAuthority()).isEqualTo("RELIABLE");
        }
    }

    @Nested
    @DisplayName("JSON deserialization with missing temporal fields")
    class MissingFieldDefaults {

        @Test
        @DisplayName("missing temporal fields default to null")
        void missingTemporalFieldsDefaultToNull() throws Exception {
            var json = """
                    {
                        "text": "some proposition",
                        "confidence": 0.8,
                        "rank": 0
                    }
                    """;

            var node = objectMapper.readValue(json, PropositionNode.class);

            assertThat(node.getValidFrom()).isNull();
            assertThat(node.getValidTo()).isNull();
            assertThat(node.getTransactionStart()).isNull();
            assertThat(node.getTransactionEnd()).isNull();
            assertThat(node.getSupersededBy()).isNull();
            assertThat(node.getSupersedes()).isNull();
        }

        @Test
        @DisplayName("explicitly null temporal fields remain null after deserialization")
        void explicitlyNullTemporalFieldsRemainNull() throws Exception {
            var json = """
                    {
                        "text": "explicit nulls",
                        "confidence": 0.7,
                        "rank": 0,
                        "validFrom": null,
                        "validTo": null,
                        "transactionStart": null,
                        "transactionEnd": null,
                        "supersededBy": null,
                        "supersedes": null
                    }
                    """;

            var node = objectMapper.readValue(json, PropositionNode.class);

            assertThat(node.getValidFrom()).isNull();
            assertThat(node.getValidTo()).isNull();
            assertThat(node.getTransactionStart()).isNull();
            assertThat(node.getTransactionEnd()).isNull();
            assertThat(node.getSupersededBy()).isNull();
            assertThat(node.getSupersedes()).isNull();
        }
    }

    @Nested
    @DisplayName("Convenience constructor defaults")
    class ConvenienceConstructorDefaults {

        @Test
        @DisplayName("two-arg constructor sets temporal fields to null")
        void twoArgConstructorSetsTemporalFieldsToNull() {
            var node = new PropositionNode("simple text", 0.5);

            assertThat(node.getValidFrom()).isNull();
            assertThat(node.getValidTo()).isNull();
            assertThat(node.getTransactionStart()).isNull();
            assertThat(node.getTransactionEnd()).isNull();
            assertThat(node.getSupersededBy()).isNull();
            assertThat(node.getSupersedes()).isNull();
        }

        @Test
        @DisplayName("twelve-arg plain proposition constructor sets temporal fields to null")
        void twelveArgConstructorSetsTemporalFieldsToNull() {
            var node = new PropositionNode(
                    "id-1", "ctx-1", "some text", 0.9, 0.1,
                    "reasoning", java.util.List.of(), Instant.now(), Instant.now(),
                    com.embabel.dice.proposition.PropositionStatus.ACTIVE, null, java.util.List.of());

            assertThat(node.getValidFrom()).isNull();
            assertThat(node.getValidTo()).isNull();
            assertThat(node.getTransactionStart()).isNull();
            assertThat(node.getTransactionEnd()).isNull();
            assertThat(node.getSupersededBy()).isNull();
            assertThat(node.getSupersedes()).isNull();
        }
    }
}
