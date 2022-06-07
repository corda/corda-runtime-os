package net.corda.crypto.merkle

import net.corda.crypto.core.concatByteArrays
import net.corda.crypto.core.toByteArray
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.merkle.IndexedMerkleLeaf
import net.corda.v5.crypto.merkle.MerkleProof
import net.corda.v5.crypto.merkle.MerkleTreeHashDigestProvider
import java.security.SecureRandom
import java.util.*

private fun createNonce(random: Random): ByteArray {
    val nonce = ByteArray(16)
    random.nextBytes(nonce)
    return nonce
}

// The classic tree algorithm (e.g. RFC6962) using 0x00 prefix for leaves and 0x01 for nodes
// and SHA2-256 double hashing throughout
// Nonce is just null for this style of provider
object DefaultHashDigestProvider : MerkleTreeHashDigestProvider {
    private val ZERO_BYTE = ByteArray(1) { 0 }
    private val ONE_BYTE = ByteArray(1) { 1 }

    override fun leafNonce(index: Int): ByteArray? = null

    override fun leafHash(index: Int, nonce: ByteArray?, bytes: ByteArray): SecureHash {
        require(nonce == null) { "Nonce must be null" }
        return SecureHash.doubleHash(concatByteArrays(ZERO_BYTE, bytes))
    }

    override fun nodeHash(depth: Int, left: SecureHash, right: SecureHash): SecureHash {
        return SecureHash.doubleHash(concatByteArrays(ONE_BYTE, left.bytes, right.bytes))
    }
}

// Simple variant of standard hashing, for use where Merkle trees are used in different roles and
// need to be different to protect against copy-paste attacks
class TweakableHashDigestProvider(
    private val leafPrefix: ByteArray,
    private val nodePrefix: ByteArray
) : MerkleTreeHashDigestProvider {
    init {
        require(!leafPrefix.contentEquals(nodePrefix)) {
            "Hash prefix for nodes must be different to that for leaves"
        }
    }

    override fun leafNonce(index: Int): ByteArray? = null

    override fun leafHash(index: Int, nonce: ByteArray?, bytes: ByteArray): SecureHash {
        require(nonce == null) { "Nonce must be null" }
        return SecureHash.doubleHash(concatByteArrays(leafPrefix, bytes))
    }

    override fun nodeHash(depth: Int, left: SecureHash, right: SecureHash): SecureHash {
        return SecureHash.doubleHash(concatByteArrays(nodePrefix, left.bytes, right.bytes))
    }
}

// This doesn't support log audit proofs as it uses depth in the node hashes
// However, it is suited to low entropy leaves, such as blockchain transactions
class NonceHashDigestProvider(val entropy: ByteArray) : MerkleTreeHashDigestProvider {
    constructor(random: Random = SecureRandom()) : this(createNonce(random))    // @todo: original implementation seems to have used DRBG.

    companion object {
        // use this instance if only verification is required and thus don't need to reveal the entropy
        val VERIFY_INSTANCE = NonceHashDigestProvider(ByteArray(0))

        val SIZE_ONLY_VERIFY_INSTANCE = object : MerkleTreeHashDigestProvider {
            override fun leafNonce(index: Int): ByteArray? = null

            override fun leafHash(index: Int, nonce: ByteArray?, bytes: ByteArray): SecureHash {
                require(nonce == null) { "Nonce must not be null" }
                return SecureHash("SHA-256", bytes) // @todo: original was: SecureHash.deserialize(bytes) but that looked too avro specific.
            }

            override fun nodeHash(depth: Int, left: SecureHash, right: SecureHash): SecureHash {
                return SecureHash.doubleHash(concatByteArrays(depth.toByteArray(), left.bytes, right.bytes))
            }
        }
    }

    fun getSizeProof(leaves: List<ByteArray>): MerkleProof {
        val merkleTree = MerkleTreeImpl.createMerkleTree(leaves, this)
        val allLeavesProof = merkleTree.createAuditProof(merkleTree.leaves.indices.toList())
        val preHashedLeaves = allLeavesProof.leaves.map {
            IndexedMerkleLeaf(it.index, null, leafHash(it.index, it.nonce, it.leafData).bytes)
        }
        return MerkleProofImpl(allLeavesProof.treeSize, preHashedLeaves, allLeavesProof.hashes)
    }

    override fun leafNonce(index: Int): ByteArray {
        require(entropy.isNotEmpty()) { "No entropy! VERIFY_INSTANCE being used to create proof by mistake?" }
        return SecureHash.secureHash(concatByteArrays(index.toByteArray(), entropy)).bytes // todo: original: .serialize()
    }

    override fun leafHash(index: Int, nonce: ByteArray?, bytes: ByteArray): SecureHash {
        require(nonce != null) { "Nonce must not be null" }
        return SecureHash.secureHash(concatByteArrays(nonce, bytes))
    }

    override fun nodeHash(depth: Int, left: SecureHash, right: SecureHash): SecureHash {
        return SecureHash.doubleHash(concatByteArrays(depth.toByteArray(), left.bytes, right.bytes))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NonceHashDigestProvider

        if (!entropy.contentEquals(other.entropy)) return false

        return true
    }

    override fun hashCode(): Int {
        return entropy.contentHashCode()
    }
}