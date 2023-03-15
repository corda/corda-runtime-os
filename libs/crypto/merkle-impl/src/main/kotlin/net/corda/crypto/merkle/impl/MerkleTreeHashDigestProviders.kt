package net.corda.crypto.merkle.impl

import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.concatByteArrays
import net.corda.crypto.core.toByteArray
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.extensions.merkle.MerkleTreeHashDigestProvider
import net.corda.v5.crypto.extensions.merkle.MerkleTreeHashDigestProviderWithSizeProofSupport
import net.corda.v5.crypto.merkle.IndexedMerkleLeaf
import net.corda.v5.crypto.merkle.MerkleProof
import net.corda.v5.crypto.merkle.MerkleProofType
import java.nio.charset.Charset
import java.security.SecureRandom

private fun createNonce(random: SecureRandom): ByteArray {
    val nonce = ByteArray(NonceHashDigestProvider.EXPECTED_ENTROPY_LENGTH)
    random.nextBytes(nonce)
    return nonce
}

// The classic tree algorithm (e.g. RFC6962) using 0x00 prefix for leaves and 0x01 for nodes
// and SHA2-256 double hashing throughout
// Nonce is just null for this style of provider
class DefaultHashDigestProvider(
    private val digestAlgorithmName: DigestAlgorithmName = DigestAlgorithmName.SHA2_256D,
    private val digestService: DigestService
) : MerkleTreeHashDigestProvider {
    private val ZERO_BYTE = ByteArray(1) { 0 }
    private val ONE_BYTE = ByteArray(1) { 1 }

    override fun getDigestAlgorithmName() = digestAlgorithmName

    override fun leafNonce(index: Int): ByteArray? = null

    override fun leafHash(index: Int, nonce: ByteArray?, bytes: ByteArray): SecureHash {
        require(nonce == null) { "Nonce must be null" }
        return digestService.hash(concatByteArrays(ZERO_BYTE, bytes), digestAlgorithmName)
    }

    override fun nodeHash(depth: Int, left: SecureHash, right: SecureHash): SecureHash {
        checkMatchingAlgorithms(left, right)
        return digestService.hash(concatByteArrays(ONE_BYTE, left.serialize(), right.serialize()), digestAlgorithmName)
    }
}

// Simple variant of standard hashing, for use where Merkle trees are used in different roles and
// need to be different to protect against copy-paste attacks
class TweakableHashDigestProvider(
    private val digestAlgorithmName: DigestAlgorithmName = DigestAlgorithmName.SHA2_256D,
    private val digestService: DigestService,
    private val leafPrefix: ByteArray,
    private val nodePrefix: ByteArray
) : MerkleTreeHashDigestProvider {
    init {
        require(!leafPrefix.contentEquals(nodePrefix)) {
            "Hash prefix for nodes must be different to that for leaves"
        }
        require(leafPrefix.size > 0) {
            "Leaf prefix cannot be empty"
        }
        require(nodePrefix.size > 0) {
            "Node prefix cannot be empty"
        }
    }

    override fun getDigestAlgorithmName() = digestAlgorithmName

    override fun leafNonce(index: Int): ByteArray? = null

    override fun leafHash(index: Int, nonce: ByteArray?, bytes: ByteArray): SecureHash {
        require(nonce == null) { "Nonce must be null" }
        return digestService.hash(concatByteArrays(leafPrefix, bytes), digestAlgorithmName)
    }

    override fun nodeHash(depth: Int, left: SecureHash, right: SecureHash): SecureHash {
        checkMatchingAlgorithms(left, right)
        return digestService.hash(concatByteArrays(nodePrefix, left.serialize(), right.serialize()), digestAlgorithmName)
    }
}

// This doesn't support log audit proofs as it uses depth in the node hashes
// However, it is suited to low entropy leaves, such as blockchain transactions
open class NonceHashDigestProvider(
    private val digestAlgorithmName: DigestAlgorithmName = DigestAlgorithmName.SHA2_256D,
    private val digestService: DigestService,
    val entropy: ByteArray,
    ) : MerkleTreeHashDigestProviderWithSizeProofSupport {
    companion object{
        val EXPECTED_ENTROPY_LENGTH = 32
    }
    constructor(
        digestAlgorithmName: DigestAlgorithmName = DigestAlgorithmName.SHA2_256D,
        digestService: DigestService,
        random: SecureRandom
    ) : this(digestAlgorithmName, digestService, createNonce(random))

    // use this class if only verification is required and thus don't need to reveal the entropy
    class Verify(
        digestAlgorithmName: DigestAlgorithmName = DigestAlgorithmName.SHA2_256D,
        digestService: DigestService
    ): NonceHashDigestProvider(digestAlgorithmName, digestService, ByteArray(0))

    class SizeOnlyVerify(
        private val digestAlgorithmName: DigestAlgorithmName = DigestAlgorithmName.SHA2_256D,
        private val digestService: DigestService
    ): MerkleTreeHashDigestProvider {
        override fun getDigestAlgorithmName() = digestAlgorithmName

        override fun leafNonce(index: Int): ByteArray? = null

        override fun leafHash(index: Int, nonce: ByteArray?, bytes: ByteArray): SecureHash {
            require(nonce == null) { "Nonce must be null" }
            return deserialize(bytes, digestService)
        }

        override fun nodeHash(depth: Int, left: SecureHash, right: SecureHash): SecureHash {
            checkMatchingAlgorithms(left, right)
            return digestService.hash(concatByteArrays(depth.toByteArray(), left.serialize(), right.serialize()), digestAlgorithmName)
        }
    }

    override fun getSizeProof(leaves: List<ByteArray>): MerkleProof {
        val merkleTree = MerkleTreeImpl.createMerkleTree(leaves, this)
        val allLeavesProof = merkleTree.createAuditProof(merkleTree.leaves.indices.toList())
        val preHashedLeaves = allLeavesProof.leaves.map {
            IndexedMerkleLeaf(it.index, null, leafHash(it.index, it.nonce, it.leafData).serialize())
        }
        return MerkleProofImpl(MerkleProofType.SIZE, allLeavesProof.treeSize, preHashedLeaves, allLeavesProof.hashes)
    }

    override fun leafNonce(index: Int): ByteArray {
        require(entropy.isNotEmpty()) { "No entropy! NonceHashDigestProvider.Verify being used to create proof by mistake?" }
        return digestService.hash(concatByteArrays(index.toByteArray(), entropy), digestAlgorithmName).serialize()
    }

    override fun leafHash(index: Int, nonce: ByteArray?, bytes: ByteArray): SecureHash {
        require(nonce != null) { "Nonce must not be null" }
        return digestService.hash(concatByteArrays(nonce, bytes), digestAlgorithmName)
    }

    override fun nodeHash(depth: Int, left: SecureHash, right: SecureHash): SecureHash {
        checkMatchingAlgorithms(left, right)
        return digestService.hash(concatByteArrays(depth.toByteArray(), left.serialize(), right.serialize()), digestAlgorithmName)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NonceHashDigestProvider

        if (!entropy.contentEquals(other.entropy)) return false

        if (digestAlgorithmName != other.digestAlgorithmName) return false

        return true
    }

    override fun hashCode(): Int {
        return 31 * digestAlgorithmName.hashCode() + entropy.contentHashCode()
    }

    override fun getDigestAlgorithmName() = digestAlgorithmName
}

private fun MerkleTreeHashDigestProvider.checkMatchingAlgorithms(left: SecureHash, right: SecureHash){
    require(
    left.algorithm == digestAlgorithmName.name &&
    left.algorithm == right.algorithm
    ) { "Nodes should use the same digest algorithm as the Hash Digest Provider! (L: ${left.algorithm} " +
            "R: ${right.algorithm} HDP: ${digestAlgorithmName.name})"}
}

const val SERIALIZATION_SEPARATOR: Char = ':'

internal fun deserialize(bytes: ByteArray, digestService: DigestService): SecureHash {
    val idxOfSeparator = bytes.indexOf(SERIALIZATION_SEPARATOR.code.toByte())
    if (idxOfSeparator == -1) {
        throw IllegalArgumentException("Provided argument: $bytes should be of format algorithm:bytes")
    }
    val digestAlgorithmName = DigestAlgorithmName(String(bytes.take(idxOfSeparator).toByteArray()))
    val data = bytes.drop(idxOfSeparator + 1).toByteArray()
    val digestLength = digestService.digestLength(digestAlgorithmName)
    return when (data.size) {
        digestLength -> SecureHashImpl(digestAlgorithmName.name, data)
        else -> throw IllegalArgumentException("Provided argument has ${data.size} bytes not $digestLength bytes: $data")
    }
}

internal fun SecureHash.serialize(): ByteArray {
    return ("$algorithm$SERIALIZATION_SEPARATOR").toByteArray(Charset.forName("UTF8")) + bytes
}