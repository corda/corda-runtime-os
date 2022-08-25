package net.corda.ledger.common.impl.transaction

import net.corda.crypto.core.concatByteArrays
import net.corda.crypto.core.toByteArray
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_ENTROPY_OPTION
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_NODE_PREFIX_OPTION
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_NONCE_NAME
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_TWEAKABLE_NAME
import net.corda.v5.crypto.merkle.MerkleTree
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import net.corda.v5.crypto.merkle.MerkleTreeHashDigestProvider
import net.corda.v5.ledger.common.transaction.PrivacySalt

internal const val ROOT_MERKLE_TREE_DIGEST_PROVIDER_NAME = HASH_DIGEST_PROVIDER_TWEAKABLE_NAME
internal val ROOT_MERKLE_TREE_DIGEST_ALGORITHM_NAME = DigestAlgorithmName.SHA2_256D

internal const val COMPONENT_MERKLE_TREE_DIGEST_PROVIDER_NAME = HASH_DIGEST_PROVIDER_NONCE_NAME
internal val COMPONENT_MERKLE_TREE_DIGEST_ALGORITHM_NAME = DigestAlgorithmName.SHA2_256D
internal val COMPONENT_MERKLE_TREE_ENTROPY_ALGORITHM_NAME = DigestAlgorithmName.SHA2_256D

const val ALL_LEDGER_METADATA_COMPONENT_GROUP_ID = 0

@CordaSerializable
class WireTransaction(
    private val merkleTreeFactory: MerkleTreeFactory,
    private val digestService: DigestService,
    val privacySalt: PrivacySalt,
    val componentGroupLists: List<List<ByteArray>>
){
    val id: SecureHash by lazy(LazyThreadSafetyMode.PUBLICATION) {
        rootMerkleTree.root
    }

    fun getComponentGroupList(componentGroupId: Int): List<ByteArray> =
        componentGroupLists[componentGroupId]

    fun getWrappedLedgerTransactionClassName(serializer: SerializationService): String {
        return this.getMetadata(serializer).getLedgerModel()
    }

    fun getMetadata(serializer: SerializationService): TransactionMetaData {
        val metadataBytes = componentGroupLists[ALL_LEDGER_METADATA_COMPONENT_GROUP_ID].first()
        return serializer.deserialize(metadataBytes, TransactionMetaData::class.java)
    }

    private fun getRootMerkleTreeDigestProvider() : MerkleTreeHashDigestProvider = merkleTreeFactory.createHashDigestProvider(
        ROOT_MERKLE_TREE_DIGEST_PROVIDER_NAME,
        ROOT_MERKLE_TREE_DIGEST_ALGORITHM_NAME,
        mapOf(
            HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION to "leaf".toByteArray(Charsets.UTF_8),
            HASH_DIGEST_PROVIDER_NODE_PREFIX_OPTION to "node".toByteArray(Charsets.UTF_8)
        )
    )

    private fun getComponentGroupEntropy(
        privacySalt: PrivacySalt,
        componentGroupIndexBytes: ByteArray
    ): ByteArray =
        digestService.hash(
            concatByteArrays(privacySalt.bytes, componentGroupIndexBytes),
            COMPONENT_MERKLE_TREE_ENTROPY_ALGORITHM_NAME
        ).bytes

    private fun getComponentGroupMerkleTreeDigestProvider(
        privacySalt: PrivacySalt,
        componentGroupIndex: Int
    ) : MerkleTreeHashDigestProvider =
        merkleTreeFactory.createHashDigestProvider(
            COMPONENT_MERKLE_TREE_DIGEST_PROVIDER_NAME,
            COMPONENT_MERKLE_TREE_DIGEST_ALGORITHM_NAME,
            mapOf(
                HASH_DIGEST_PROVIDER_ENTROPY_OPTION to
                    getComponentGroupEntropy(privacySalt, componentGroupIndex.toByteArray())
            )
    )

    private val rootMerkleTree: MerkleTree by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val componentGroupRoots = mutableListOf<ByteArray>()
        componentGroupLists.forEachIndexed{ index, leaves: List<ByteArray> ->
            val componentMerkleTree = merkleTreeFactory.createTree(
                leaves,
                getComponentGroupMerkleTreeDigestProvider(privacySalt, index)
            )
            componentGroupRoots += componentMerkleTree.root.bytes
        }

        merkleTreeFactory.createTree(componentGroupRoots, getRootMerkleTreeDigestProvider())
    }
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WireTransaction) return false
        if (!other.privacySalt.bytes.contentEquals(privacySalt.bytes)) return false
        if (other.componentGroupLists.size != componentGroupLists.size) return false

        return (other.componentGroupLists.withIndex().all { i ->
            i.value.size == componentGroupLists[i.index].size &&
                i.value.withIndex().all { j ->
                    j.value.contentEquals(componentGroupLists[i.index][j.index])
                }
        })
    }

    override fun hashCode(): Int = privacySalt.hashCode() + componentGroupLists.hashCode() * 31
}
