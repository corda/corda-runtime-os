package net.corda.v5.application.crypto

import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.merkle.MerkleTree
import net.corda.v5.crypto.merkle.MerkleTreeHashDigest

/**
 * [MerkleTreeFactory] creates [MerkleTree]s and [MerkleTreeHashDigest]s.
 */
@DoNotImplement
interface MerkleTreeFactory {
    /**
     * Creates a [MerkleTree]
     *
     * @param leaves The leaves of the tree.
     * @param digest Merkle Tree Hash digest used to construct the tree's node and leaf hashes.
     *
     * @return A new [MerkleTree] instance.
     */
    @Suspendable
    fun createTree(
        leaves: List<ByteArray>,
        digest: MerkleTreeHashDigest
    ) : MerkleTree

    /**
     * Creates a [MerkleTreeHashDigest].
     *
     * @param merkleTreeHashDigestProviderName name of the hash digest provider class
     * @param digestAlgorithmName name of the base hash algorithm
     *
     * @return A new [MerkleTreeHashDigest] instance.
     */
    @Suspendable
    fun createHashDigest(
        merkleTreeHashDigestProviderName: String,
        digestAlgorithmName: DigestAlgorithmName,
    ) : MerkleTreeHashDigest

    /**
     * Creates a [MerkleTreeHashDigest].
     *
     * @param merkleTreeHashDigestProviderName name of the hash digest provider class
     * @param digestAlgorithmName name of the base hash algorithm
     * @param options Hash digest provider specific options
     *
     * @return A new [MerkleTreeHashDigest] instance.
     */
    @Suspendable
    fun createHashDigest(
        merkleTreeHashDigestProviderName: String,
        digestAlgorithmName: DigestAlgorithmName,
        options: Map<String, Any>,
    ) : MerkleTreeHashDigest
}
