package net.corda.v5.crypto.merkle;

import net.corda.v5.base.exceptions.CordaRuntimeException;
import org.jetbrains.annotations.NotNull;

/**
 * Indicates that the calculation of the root hash of a {@link MerkleProof} failed.
 */
public final class MerkleProofRebuildFailureException extends CordaRuntimeException {
    public MerkleProofRebuildFailureException(@NotNull String message) {
        super(message, null);
    }
}
