package net.corda.ledger.common.data.transaction

import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.crypto.core.concatByteArrays
import net.corda.crypto.core.toByteArray
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.extensions.merkle.MerkleTreeHashDigestProvider
import net.corda.v5.crypto.merkle.HashDigestConstants.HASH_DIGEST_PROVIDER_ENTROPY_OPTION
import net.corda.v5.crypto.merkle.MerkleTree
import net.corda.v5.ledger.common.transaction.PrivacySalt
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.common.transaction.TransactionWithMetadata
import java.util.Objects
import java.util.concurrent.ConcurrentHashMap

class WireTransaction(
    private val merkleTreeProvider: MerkleTreeProvider,
    private val digestService: DigestService,
    val privacySalt: PrivacySalt,
    val componentGroupLists: List<List<ByteArray>>,
    private val metadata: TransactionMetadata
) : TransactionWithMetadata {

    fun getComponentGroupList(componentGroupId: Int): List<ByteArray> =
        componentGroupLists[componentGroupId]

    val wrappedLedgerTransactionClassName: String
        get() = metadata.getLedgerModel()

    private val componentMerkleTreeEntropyAlgorithmName
        get() = DigestAlgorithmName(
            getDigestSetting(
                COMPONENT_MERKLE_TREE_ENTROPY_ALGORITHM_NAME_KEY
            )
        )

    private val componentMerkleTreeDigestProviderName
        get() = getDigestSetting(
            COMPONENT_MERKLE_TREE_DIGEST_PROVIDER_NAME_KEY
        )

    private val componentMerkleTreeDigestAlgorithmName
        get() = DigestAlgorithmName(
            getDigestSetting(
                COMPONENT_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY
            )
        )

    private fun getComponentGroupEntropy(
        privacySalt: PrivacySalt,
        componentGroupIndexBytes: ByteArray
    ): ByteArray =
        digestService.hash(
            concatByteArrays(privacySalt.bytes, componentGroupIndexBytes),
            componentMerkleTreeEntropyAlgorithmName
        ).bytes

    fun getComponentGroupMerkleTreeDigestProvider(
        privacySalt: PrivacySalt,
        componentGroupIndex: Int
    ): MerkleTreeHashDigestProvider =
        merkleTreeProvider.createHashDigestProvider(
            componentMerkleTreeDigestProviderName,
            componentMerkleTreeDigestAlgorithmName,
            mapOf(
                HASH_DIGEST_PROVIDER_ENTROPY_OPTION to
                        getComponentGroupEntropy(privacySalt, componentGroupIndex.toByteArray())
            )
        )

    val componentMerkleTrees: ConcurrentHashMap<Int, MerkleTree> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        ConcurrentHashMap(componentGroupLists.mapIndexed { index, group ->
            index to merkleTreeProvider.createTree(
                group.ifEmpty { listOf(ByteArray(0)) },
                getComponentGroupMerkleTreeDigestProvider(privacySalt, index)
            )
        }.toMap())
    }

    val rootMerkleTree: MerkleTree by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val componentGroupRoots: List<ByteArray> = List(componentGroupLists.size) { index ->
            componentMerkleTrees[index]!!.root.bytes
        }

        merkleTreeProvider.createTree(componentGroupRoots, getRootMerkleTreeDigestProvider(merkleTreeProvider))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WireTransaction) return false
        if (other.privacySalt != privacySalt) return false
        if (other.componentGroupLists.size != componentGroupLists.size) return false

        return (other.componentGroupLists.withIndex().all { i ->
            i.value.size == componentGroupLists[i.index].size &&
                    i.value.withIndex().all { j ->
                        j.value contentEquals componentGroupLists[i.index][j.index]
                    }
        })
    }

    override fun hashCode(): Int = Objects.hash(privacySalt, componentGroupLists)

    override fun toString(): String {
        return "WireTransaction(" +
                "id=$id, " +
                "privacySalt=$privacySalt, " +
                "metadata=$metadata, componentGroupLists=${
                    componentGroupLists.map { group ->
                        group.map { component -> "(size=${component.size}, sum=${component.sum()})" }
                    }
                }" +
                ")"
    }

    override fun getId(): SecureHash {
        return rootMerkleTree.root
    }

    override fun getMetadata(): TransactionMetadata {
        return metadata
    }
}
