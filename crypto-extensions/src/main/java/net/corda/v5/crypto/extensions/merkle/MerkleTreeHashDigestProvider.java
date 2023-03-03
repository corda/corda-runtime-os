package net.corda.v5.crypto.extensions.merkle;

import net.corda.v5.crypto.SecureHash;
import net.corda.v5.crypto.merkle.MerkleTree;
import net.corda.v5.crypto.merkle.MerkleTreeHashDigest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Different use cases require different {@link MerkleTree} calculations.
 * {@link MerkleTreeHashDigestProvider}s let us specify the
 * 
 * <ul>
 * <li> Leaf Nonce </li>
 * <li> Leaf Hash </li>
 * <li> Node Hash calculation methods </li>
 * <li> Base Digest Algorithm </li>
 * </ul>
 */

public interface MerkleTreeHashDigestProvider extends MerkleTreeHashDigest {
    /**
     * Calculates the nonce for a leaf.
     *
     * @param index The leaf's index.
     * @return byte array of the nonce
     */
    @Nullable
    byte[] leafNonce(int index);

    /**
     * Calculates the hash of a leaf.
     *
     * @param index The leaf's index.
     * @param nonce The leaf's nonce.
     * @param bytes The leaf's content bytes.
     */
    @NotNull
    SecureHash leafHash(int index, @Nullable byte[] nonce, @NotNull byte[] bytes);

    /**
     * Calculates the hash of a node.
     *
     * @param depth Depth of the node.
     * @param left  {@link SecureHash} of the left child of the node.
     * @param right {@link SecureHash} of the right child of the node.
     */
    @NotNull
    SecureHash nodeHash(int depth, @NotNull SecureHash left, @NotNull SecureHash right);

}
