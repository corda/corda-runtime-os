package net.corda.v5.crypto.merkle;

import net.corda.v5.base.annotations.CordaSerializable;
import net.corda.v5.crypto.SecureHash;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * {@link MerkleProof}s can be used to verify if some specific data is part of a {@link MerkleTree}.
 * <p>
 * Use {@link MerkleTree#createAuditProof} to create a proof for a set of leaves for an
 * existing {@link MerkleTree}.
 * Construct a {@link MerkleProof} from its (<code>treeSize</code>, <code>leaves</code>, <code>hashes</code>) 
 * when you want to <code>verify</code>} if the leaves to be checked are part of a {@link MerkleTree} with the specific root.
 */
@CordaSerializable
public interface MerkleProof {

    /**
     * Get the type of the proof
     *
     * @return Type of the proof
     */
    @NotNull
    MerkleProofType getProofType();

    int getTreeSize();

    List<IndexedMerkleLeaf> getLeaves();

    List<SecureHash> getHashes();

    /**
     * Checks if the {@link MerkleProof} has been generated from a {@link MerkleTree} with the given root.
     *
     * @param root   The root of the tree to be verified.
     * @param digest The tree's digest.
     * @return Result of the verification.
     */
    boolean verify(@NotNull SecureHash root, @NotNull MerkleTreeHashDigest digest);

    /**
     * Rebuilds the {@link MerkleTree} from the {@link MerkleProof} and returns its root {@link SecureHash}.
     *
     * @param digest The tree's digest.
     * @return Root hash of the tree.
     * @throws MerkleProofRebuildFailureException if the calculation of the root hash failed.
     */
    SecureHash calculateRoot(@NotNull MerkleTreeHashDigest digest);
}
