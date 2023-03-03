package net.corda.v5.crypto.merkle;

import org.jetbrains.annotations.NotNull;

/**
 * An implementation of RFC6962 compatible merkle tree
 */
public final class HashDigestConstants {
    private HashDigestConstants() {
        // No construction allowed; this class exists to contain its static string properties. 
    }

    @NotNull
    public static final String HASH_DIGEST_PROVIDER_DEFAULT_NAME = "DefaultHashDigestProvider";

    /**
     * A merkle tree digest that one-way derives a set of per-leaf nonces from a 32-byte entropy input. The nonces are
     * pre-hashed with the leaf data before forming a standard merkle tree.
     * This allows low entropy inputs to be used in the merkle tree.
     * Requires <code>HASH_DIGEST_PROVIDER_ENTROPY_OPTION</code>
     */
    @NotNull
    public static final String HASH_DIGEST_PROVIDER_NONCE_NAME = "NonceHashDigestProvider";

    /**
     * When verifying a NONCE digest based MerkleProof the source entropy is not communicated to the verifier.
     * Instead, only the per-leaf nonces are transmitted.
     * Use the <code>NONCE_VERIFY</code> digest to validate the MerkleProof against the expected root hash.
     */
    @NotNull
    public static final String HASH_DIGEST_PROVIDER_NONCE_VERIFY_NAME = "NonceHashDigestProviderVerify";

    /**
     * Using the NONCE digest it is possible to create a {@link MerkleProof} that doesn't reveal the leaf data, but
     * provides supporting evidence of the number of leaves.
     * These MerkleProofs can be validated against an expected root hash using the <code>NONCE_SIZE_ONLY_VERIFY</code> digest
     */
    @NotNull
    public static final String HASH_DIGEST_PROVIDER_NONCE_SIZE_ONLY_VERIFY_NAME = "NonceHashDigestProviderSizeOnlyVerify";

    /**
     * This provides an extension of the <code>DEFAULT</code> hash with configurable hashes for leaf and internal Merkle node operations.
     * This is required when similar Merkle trees are used to generate a hash that is signed over so
     * that replay attacks can be prevented.
     * Requires <code>HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION</code> and
     * <code>HASH_DIGEST_PROVIDER_NODE_PREFIX_OPTION</code>
     */
    @NotNull
    public static final String HASH_DIGEST_PROVIDER_TWEAKABLE_NAME = "TweakableHashDigestProvider";

    /**
     * Tweakable hash digest providers require a non-empty ByteArray leaf prefix option.
     */
    @NotNull
    public static final String HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION = "leafPrefix";

    /**
     * Tweakable hash digest providers require a non-empty ByteArray node prefix option.
     */
    @NotNull
    public static final String HASH_DIGEST_PROVIDER_NODE_PREFIX_OPTION = "nodePrefix";

    /**
     * Nonce hash digest providers require a 32 bytes long ByteArray entropy option.
     */
    @NotNull
    public static final String HASH_DIGEST_PROVIDER_ENTROPY_OPTION = "entropy";
}