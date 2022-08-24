package net.corda.v5.ledger.obsolete.crypto

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.obsolete.merkle.MerkleTree

/**
 * A class that holds a map of digest services used for hashing transaction components and
 * label the transaction's Merkle tree leaves and nodes.
 *
 * The mapping is provided by the crypto-service API and defined by the membership group.
 */
@CordaSerializable
data class TransactionDigestAlgorithmNames(var names: Map<String, DigestAlgorithmName>) {
    // This is a default constructor that is only used temporarily to provide a default
    // configuration until the crypto-service API is complete.
    constructor() : this(
        mapOf(
            Pair(TransactionDigestType.TREE, DigestAlgorithmName("SHA-256")),
            Pair(TransactionDigestType.COMPONENTHASH, DigestAlgorithmName("SHA-256D")),
            Pair(TransactionDigestType.COMPONENTNONCE, DigestAlgorithmName("SHA-256")),
            Pair(TransactionDigestType.COMPONENTNONCEHASH, DigestAlgorithmName("SHA-256"))
        )
    )

    init {
        check(names[TransactionDigestType.COMPONENTHASH] != null) { "TransactionDigestType.COMPONENTHASH must map to a digest algorithm name" }
        check(names[TransactionDigestType.COMPONENTNONCE] != null) { "TransactionDigestType.COMPONENTNONCE must map to a digest algorithm name" }
    }

    fun hash(bytes: ByteArray, hashingService: DigestService): SecureHash =
        names.getValue(TransactionDigestType.TREE).run {
            hashingService.hash(bytes, this)
        }

    fun getMerkleTree(allLeavesHashes: List<SecureHash>, hashingService: DigestService) =
        MerkleTree.getMerkleTree(allLeavesHashes, names.getValue(TransactionDigestType.TREE), hashingService)

    fun isAlgorithmSupported(hashAlgorithm: String): Boolean =
        names.getValue(TransactionDigestType.TREE).name == hashAlgorithm
}