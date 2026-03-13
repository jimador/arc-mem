package dev.arcmem.core.assembly.protection;
import dev.arcmem.core.memory.budget.*;
import dev.arcmem.core.memory.canon.*;
import dev.arcmem.core.memory.conflict.*;
import dev.arcmem.core.memory.engine.*;
import dev.arcmem.core.memory.maintenance.*;
import dev.arcmem.core.memory.model.*;
import dev.arcmem.core.memory.mutation.*;
import dev.arcmem.core.memory.trust.*;
import dev.arcmem.core.assembly.budget.*;
import dev.arcmem.core.assembly.compaction.*;
import dev.arcmem.core.assembly.compliance.*;
import dev.arcmem.core.assembly.protection.*;
import dev.arcmem.core.assembly.retrieval.*;

/**
 * Vocabulary mask for constrained decoding.
 * <p>
 * At each decoding step, tokens where {@code allowedTokens[i] == false} have their
 * logits set to negative infinity, making them structurally impossible to generate.
 * This is the STATIC architecture adapted to the arc-mem domain.
 *
 * @param allowedTokens  boolean mask over the full vocabulary; {@code allowedTokens[i] == false}
 *                       means token {@code i} is suppressed at every decoding step
 * @param constraintCount number of unit constraints encoded in this mask
 * @param vocabularySize  total vocabulary size (length of {@code allowedTokens})
 */
public record ConstraintMask(
        boolean[] allowedTokens,
        int constraintCount,
        int vocabularySize
) {}
