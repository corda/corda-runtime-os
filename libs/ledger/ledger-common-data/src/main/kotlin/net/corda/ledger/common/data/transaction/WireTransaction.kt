package net.corda.ledger.common.data.transaction

import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.crypto.core.bytes
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.merkle.MerkleTree
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.common.transaction.TransactionWithMetadata
import java.util.Objects
import java.util.concurrent.ConcurrentHashMap

@CordaSerializable
class WireTransaction(
    private val merkleTreeProvider: MerkleTreeProvider,
    private val digestService: DigestService,
    val privacySalt: PrivacySalt,
    val componentGroupLists: List<List<ByteArray>>,
    private val metadata: TransactionMetadata
) : TransactionWithMetadata {

    init {
        check((metadata as TransactionMetadataInternal).getNumberOfComponentGroups() == componentGroupLists.size) {
            "Number of component groups in metadata structure description does not match with the real number!"
        }
    }

    fun getComponentGroupList(componentGroupId: Int): List<ByteArray> =
        componentGroupLists[componentGroupId]

    val wrappedLedgerTransactionClassName: String
        get() = metadata.getLedgerModel()

    val componentMerkleTrees: ConcurrentHashMap<Int, MerkleTree> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        ConcurrentHashMap(
            componentGroupLists.mapIndexed { index, group ->
                index to merkleTreeProvider.createTree(
                    group.ifEmpty { listOf(ByteArray(0)) },
                    metadata.getComponentGroupMerkleTreeDigestProvider(privacySalt, index, merkleTreeProvider, digestService)
                )
            }.toMap()
        )
    }

    val rootMerkleTree: MerkleTree by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val componentGroupRoots: List<ByteArray> = List(componentGroupLists.size) { index ->
            componentMerkleTrees[index]!!.root.bytes
        }

        merkleTreeProvider.createTree(componentGroupRoots, metadata.getRootMerkleTreeDigestProvider(merkleTreeProvider))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WireTransaction) return false
        if (other.privacySalt != privacySalt) return false
        if (other.componentGroupLists.size != componentGroupLists.size) return false

        return (
            other.componentGroupLists.withIndex().all { i ->
                i.value.size == componentGroupLists[i.index].size &&
                    i.value.withIndex().all { j ->
                        j.value contentEquals componentGroupLists[i.index][j.index]
                    }
            }
            )
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
