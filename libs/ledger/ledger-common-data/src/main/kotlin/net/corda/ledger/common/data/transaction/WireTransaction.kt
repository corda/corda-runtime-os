package net.corda.ledger.common.data.transaction

import net.corda.crypto.core.concatByteArrays
import net.corda.crypto.core.toByteArray
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.extensions.merkle.MerkleTreeHashDigestProvider
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_ENTROPY_OPTION
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_NODE_PREFIX_OPTION
import net.corda.v5.crypto.merkle.MerkleTree
import net.corda.v5.ledger.common.transaction.PrivacySalt
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.Objects

class WireTransaction(
    private val merkleTreeProvider: MerkleTreeProvider,
    private val digestService: DigestService,
    val privacySalt: PrivacySalt,
    val componentGroupLists: List<List<ByteArray>>,
    val metadata: TransactionMetadata
) {
    val id: SecureHash by lazy(LazyThreadSafetyMode.PUBLICATION) {
        rootMerkleTree.root
    }

    fun getComponentGroupList(componentGroupId: Int): List<ByteArray> =
        componentGroupLists[componentGroupId]

    val wrappedLedgerTransactionClassName: String
        get() {
            return metadata.getLedgerModel()
        }

    private fun getDigestSetting(settingKey: String): Any {
        return metadata.getDigestSettings()[settingKey]!!
    }

    private val rootMerkleTreeDigestProviderName
        get() =
            getDigestSetting(ROOT_MERKLE_TREE_DIGEST_PROVIDER_NAME_KEY) as String

    private val rootMerkleTreeDigestAlgorithmName
        get() = DigestAlgorithmName(
            getDigestSetting(
                ROOT_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY
            ) as String
        )

    private val rootMerkleTreeDigestOptionsLeafPrefix
        get() = Base64.getDecoder()
            .decode(getDigestSetting(ROOT_MERKLE_TREE_DIGEST_OPTIONS_LEAF_PREFIX_B64_KEY) as String)

    private val rootMerkleTreeDigestOptionsNodePrefix
        get() = Base64.getDecoder()
            .decode(getDigestSetting(ROOT_MERKLE_TREE_DIGEST_OPTIONS_NODE_PREFIX_B64_KEY) as String)

    private val componentMerkleTreeEntropyAlgorithmName
        get() = DigestAlgorithmName(
            getDigestSetting(
                COMPONENT_MERKLE_TREE_ENTROPY_ALGORITHM_NAME_KEY
            ) as String
        )

    private val componentMerkleTreeDigestProviderName
        get() = getDigestSetting(
            COMPONENT_MERKLE_TREE_DIGEST_PROVIDER_NAME_KEY
        ) as String

    private val componentMerkleTreeDigestAlgorithmName
        get() = DigestAlgorithmName(
            getDigestSetting(
                COMPONENT_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY
            ) as String
        )

    private fun getRootMerkleTreeDigestProvider(): MerkleTreeHashDigestProvider =
        merkleTreeProvider.createHashDigestProvider(
            rootMerkleTreeDigestProviderName,
            rootMerkleTreeDigestAlgorithmName,
            mapOf(
                HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION to rootMerkleTreeDigestOptionsLeafPrefix,
                HASH_DIGEST_PROVIDER_NODE_PREFIX_OPTION to rootMerkleTreeDigestOptionsNodePrefix
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

        merkleTreeProvider.createTree(componentGroupRoots, getRootMerkleTreeDigestProvider())
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
}
