@file:JvmName("HashDigestConstants")
package net.corda.v5.crypto.merkle

import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.annotations.Suspendable

/**
 * An implementation of rfc6962 compatible merkle tree
 */
const val HASH_DIGEST_PROVIDER_DEFAULT_NAME = "DefaultHashDigestProvider"

/**
 * A merkle tree digest that one-way derives a set of per-leaf nonces from a 32-byte entropy input. The nonces are
 * pre-hashed with the leaf data before forming a standard merkle tree.
 * This allows low entropy inputs to be used in the merkle tree.
 * Requires [HASH_DIGEST_PROVIDER_ENTROPY_OPTION]
 */
const val HASH_DIGEST_PROVIDER_NONCE_NAME = "NonceHashDigestProvider"

/**
 * When verifying a NONCE digest based MerkleProof the source entropy is not communicated to the verifier.
 * Instead, only the per-leaf nonces are transmitted.
 * Use the NONCE_VERIFY digest to validate the MerkleProof against the expected root hash.
 */
const val HASH_DIGEST_PROVIDER_NONCE_VERIFY_NAME = "NonceHashDigestProviderVerify"

/**
 * Using the NONCE digest it is possible to create a MerkleProof that doesn't reveal the leaf data, but
 * provides supporting evidence of the number of leaves.
 * These MerkleProofs can be validated against an expected root hash using the NONCE_SIZE_ONLY_VERIFY digest
 */
const val HASH_DIGEST_PROVIDER_NONCE_SIZE_ONLY_VERIFY_NAME = "NonceHashDigestProviderSizeOnlyVerify"

/**
 * This provides an extension of the DEFAULT hash with configurable hashes for leaf and internal Merkle node operations.
 * This is required when similar Merkle trees are used to generate a hash that is signed over so
 * that replay attacks can be prevented.
 * Requires [HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION] and [HASH_DIGEST_PROVIDER_NODE_PREFIX_OPTION]
 */
const val HASH_DIGEST_PROVIDER_TWEAKABLE_NAME = "TweakableHashDigestProvider"

/**
 * Tweakable hash digest providers require a non-empty ByteArray leaf prefix option.
 */
const val HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION = "leafPrefix"

/**
 * Tweakable hash digest providers require a non-empty ByteArray node prefix option.
 */
const val HASH_DIGEST_PROVIDER_NODE_PREFIX_OPTION = "nodePrefix"

/**
 * Nonce hash digest providers require a 32 bytes long ByteArray entropy option.
 */
const val HASH_DIGEST_PROVIDER_ENTROPY_OPTION = "entropy"

/**
 * [MerkleTreeFactory] creates [MerkleTree]s and [MerkleTreeHashDigestProvider]s.
 */
@DoNotImplement
interface MerkleTreeFactory {
    /**
     * Creates a [MerkleTree]
     *
     * @param leaves The leaves of the tree.
     * @param digestProvider Merkle Tree Hash digest provider used to construct the tree's node and leaf hashes.
     *
     * @return A new [MerkleTree] instance.
     */
    @Suspendable
    fun createTree(
        leaves: List<ByteArray>,
        digestProvider: MerkleTreeHashDigestProvider
    ) : MerkleTree

    /**
     * Creates a [MerkleTreeHashDigestProvider].
     *
     * @param merkleTreeHashDigestProviderName name of the Hash Digest Provider class
     * @param digestAlgorithmName name of the base Hash algorithm
     * @param options Hash Digest provider specific options
     *
     * @return A new [MerkleTreeHashDigestProvider] instance.
     */
    @Suspendable
    fun createHashDigestProvider(
        merkleTreeHashDigestProviderName: String,
        digestAlgorithmName: DigestAlgorithmName,
        options: Map<String, Any> = emptyMap(),
    ) : MerkleTreeHashDigestProvider
}
