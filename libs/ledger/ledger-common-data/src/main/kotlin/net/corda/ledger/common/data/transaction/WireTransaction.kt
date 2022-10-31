package net.corda.ledger.common.data.transaction

import net.corda.crypto.core.concatByteArrays
import net.corda.crypto.core.toByteArray
import net.corda.v5.cipher.suite.DigestService
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
import java.util.Objects

class WireTransaction(
    private val merkleTreeProvider: MerkleTreeProvider,
    private val digestService: DigestService,
    val privacySalt: PrivacySalt,
    val componentGroupLists: List<List<ByteArray>>,
    val metadata: TransactionMetaData
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

    private fun getComponentGroupMerkleTreeDigestProvider(
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

    private val rootMerkleTree: MerkleTree by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val componentGroupRoots = mutableListOf<ByteArray>()
        componentGroupLists.forEachIndexed { index, leaves: List<ByteArray> ->
            val componentMerkleTree = merkleTreeProvider.createTree(
                leaves,
                getComponentGroupMerkleTreeDigestProvider(privacySalt, index)
            )
            componentGroupRoots += componentMerkleTree.root.bytes
        }

        merkleTreeProvider.createTree(componentGroupRoots, getRootMerkleTreeDigestProvider())
    }

    override fun equals(other: Any?): Boolean =
        this === other
                || other is WireTransaction
                && other.privacySalt == privacySalt
                && other.componentGroupLists == componentGroupLists

    override fun hashCode(): Int = Objects.hash(privacySalt, componentGroupLists)
}
