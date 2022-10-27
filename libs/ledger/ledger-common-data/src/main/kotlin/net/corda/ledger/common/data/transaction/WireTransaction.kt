package net.corda.ledger.common.data.transaction

import net.corda.crypto.core.concatByteArrays
import net.corda.crypto.core.toByteArray
import net.corda.v5.application.marshalling.JsonMarshallingService
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

const val ALL_LEDGER_METADATA_COMPONENT_GROUP_ID = 0

class WireTransaction(
    private val merkleTreeProvider: MerkleTreeProvider,
    private val digestService: DigestService,
    jsonMarshallingService: JsonMarshallingService,
    val privacySalt: PrivacySalt,
    val componentGroupLists: List<List<ByteArray>>
){
    val id: SecureHash by lazy(LazyThreadSafetyMode.PUBLICATION) {
        rootMerkleTree.root
    }

    val metadata: TransactionMetaData

    init {
        check(componentGroupLists.all { it.isNotEmpty() }) { "Empty component groups are not allowed" }
        check(componentGroupLists.all { i -> i.all { j-> j.isNotEmpty() } }) { "Empty components are not allowed" }

        val metadataBytes = componentGroupLists[ALL_LEDGER_METADATA_COMPONENT_GROUP_ID].first()
        // TODO(update with CORE-6890)
        metadata = jsonMarshallingService.parse(metadataBytes.decodeToString(), TransactionMetaData::class.java)

        check(metadata.getDigestSettings() == WireTransactionDigestSettings.defaultValues) {
            "Only the default digest settings are acceptable now! ${metadata.getDigestSettings()} vs " +
                    "${WireTransactionDigestSettings.defaultValues}"
        }
    }

    fun getComponentGroupList(componentGroupId: Int): List<ByteArray> =
        componentGroupLists[componentGroupId]

    val wrappedLedgerTransactionClassName: String
        get() {
            return this.metadata.getLedgerModel()
        }

    private fun getDigestSetting(settingKey: String): Any {
        return this.metadata.getDigestSettings()[settingKey]!!
    }

    private val rootMerkleTreeDigestProviderName get() =
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

    private fun getRootMerkleTreeDigestProvider() : MerkleTreeHashDigestProvider =
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
    ) : MerkleTreeHashDigestProvider =
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
        componentGroupLists.forEachIndexed{ index, leaves: List<ByteArray> ->
            val componentMerkleTree = merkleTreeProvider.createTree(
                leaves,
                getComponentGroupMerkleTreeDigestProvider(privacySalt, index)
            )
            componentGroupRoots += componentMerkleTree.root.bytes
        }

        merkleTreeProvider.createTree(componentGroupRoots, getRootMerkleTreeDigestProvider())
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
