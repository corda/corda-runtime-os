package net.corda.v5.crypto.merkle

import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash

/**
 * Different use cases require different [MerkleTree] calculations.
 * [MerkleTreeHashDigestProvider]s let us specify the
 *  - Leaf Nonce
 *  - Leaf Hash
 *  - Node Hash calculation methods and
 *  - the base Digest Algorithm.
 */

interface MerkleTreeHashDigestProvider {
    val digestAlgorithmName: DigestAlgorithmName

    fun leafNonce(index: Int): ByteArray?
    fun leafHash(index: Int, nonce: ByteArray?, bytes: ByteArray): SecureHash
    fun nodeHash(depth: Int, left: SecureHash, right: SecureHash): SecureHash
}

interface MerkleTreeHashDigestProviderWithSizeProofSupport : MerkleTreeHashDigestProvider {
    fun getSizeProof(leaves: List<ByteArray>): MerkleProof
}