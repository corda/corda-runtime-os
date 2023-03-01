package net.corda.v5.application.crypto;

import net.corda.v5.crypto.DigestAlgorithmName;
import net.corda.v5.base.annotations.DoNotImplement;
import net.corda.v5.base.annotations.Suspendable;
import net.corda.v5.crypto.merkle.MerkleTree;
import net.corda.v5.crypto.merkle.MerkleTreeHashDigest;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * {@link MerkleTreeFactory} creates {@link MerkleTree}s and {@link MerkleTreeHashDigest}s.
 */
@DoNotImplement
public interface MerkleTreeFactory {
    /**
     * Creates a {@link MerkleTree}.
     *
     * @param leaves The leaves of the tree.
     * @param digest Merkle Tree Hash digest used to construct the tree's node and leaf hashes.
     *
     * @return A new [MerkleTree] instance.
     */
    @Suspendable
    @NotNull
    MerkleTree createTree(
        @NotNull List<byte[]> leaves,
        @NotNull MerkleTreeHashDigest digest
    );

    /**
     * Creates a {@link MerkleTreeHashDigest}.
     *
     * @param merkleTreeHashDigestProviderName Name of the hash digest provider class.
     * @param digestAlgorithmName Name of the base hash algorithm.
     *
     * @return A new {@link MerkleTreeHashDigest} instance.
     */
    @Suspendable
    @NotNull
    MerkleTreeHashDigest createHashDigest(
        @NotNull String merkleTreeHashDigestProviderName,
        @NotNull DigestAlgorithmName digestAlgorithmName
    );

    /**
     * Creates a {@link MerkleTreeHashDigest}.
     *
     * @param merkleTreeHashDigestProviderName Name of the hash digest provider class.
     * @param digestAlgorithmName Name of the base hash algorithm.
     * @param options Hash digest provider-specific options.
     *
     * @return A new {@link MerkleTreeHashDigest} instance.
     */
    @Suspendable
    @NotNull
    MerkleTreeHashDigest createHashDigest(
        @NotNull String merkleTreeHashDigestProviderName,
        @NotNull DigestAlgorithmName digestAlgorithmName,
        @NotNull Map<String, Object> options
    );
}
