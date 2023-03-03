package net.corda.v5.crypto.extensions.merkle;

import net.corda.v5.crypto.merkle.MerkleProof;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Special Digest provider for supporting size proofs.
 */

public interface MerkleTreeHashDigestProviderWithSizeProofSupport extends MerkleTreeHashDigestProvider {
    /**
     * Returns a size proof that reveals the number of leaves in the Merkle tree, but not the content of the leaves.
     *
     * @param leaves The tree's leaves.
     */
    @NotNull
    MerkleProof getSizeProof(@NotNull List<byte[]> leaves);
}
