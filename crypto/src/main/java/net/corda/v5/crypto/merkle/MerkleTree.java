package net.corda.v5.crypto.merkle;

import net.corda.v5.crypto.SecureHash;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * {@link MerkleTree}s are cryptographic data structures which can be used to create a short fingerprint of a larger
 * structured dataset.
 * From {@link MerkleTree}s, we can create {@link MerkleProof}s which let us prove that some particular data is part of the whole
 * tree without revealing the remaining data.
 */
public interface MerkleTree {

    /**
     * Return the input data elements. Usually something deterministically serialized.
     *
     * @return The input data elements.
     */
    @NotNull
    List<byte[]> getLeaves();

    /**
     * Return the {@link MerkleTreeHashDigest} used to construct the tree's node and leaf hashes.
     *
     * @return The digest
     */
    @NotNull
    MerkleTreeHashDigest getDigest();

    /**
     * Return the root element of the tree which is essentially the fingerprint of the whole tree/data set.
     *
     * @return The root element hash
     */
    @NotNull
    SecureHash getRoot();

    /**
     * Creates a {@link MerkleProof} for a set of leaves.
     *
     * @param leafIndices whose leaf's inclusion is to be proven by the proof.
     * @return {@link MerkleProof} for the input leaves.
     */
    @NotNull
    MerkleProof createAuditProof(@NotNull List<Integer> leafIndices);
}
