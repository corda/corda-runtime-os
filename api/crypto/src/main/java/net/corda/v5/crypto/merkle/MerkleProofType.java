package net.corda.v5.crypto.merkle;

import net.corda.v5.base.annotations.CordaSerializable;

/**
 * {@link MerkleProofType} represents what type of {@link MerkleProof} was created.
 */
@CordaSerializable
public enum MerkleProofType {
    AUDIT, SIZE
}