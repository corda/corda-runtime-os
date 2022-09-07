package net.corda.ledger.common.impl.transaction

import net.corda.crypto.core.concatByteArrays
import net.corda.crypto.core.toByteArray
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.PrivacySalt
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_NODE_PREFIX_OPTION
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_ENTROPY_OPTION
import net.corda.v5.crypto.merkle.MerkleTree
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import net.corda.v5.crypto.merkle.MerkleTreeHashDigestProvider
import java.util.Base64

const val ALL_LEDGER_METADATA_COMPONENT_GROUP_ID = 0

class WireTransaction(
    private val merkleTreeFactory: MerkleTreeFactory,
    private val digestService: DigestService,
    val privacySalt: PrivacySalt,
    val componentGroupLists: List<List<ByteArray>>
){
    val id: SecureHash by lazy(LazyThreadSafetyMode.PUBLICATION) {
        rootMerkleTree.root
    }

    init {
        check(componentGroupLists.all { it.isNotEmpty() }) { "Empty component groups are not allowed" }
        check(componentGroupLists.all { i -> i.all { j-> j.isNotEmpty() } }) { "Empty components are not allowed" }
        check(getMetadata().getDigestSettings() == WireTransactionDigestSettings.defaultValues) {
            "Only the default digest settings are acceptable now! ${getMetadata().getDigestSettings()} vs " +
                    "${WireTransactionDigestSettings.defaultValues}"
        }
    }

    fun getComponentGroupList(componentGroupId: Int): List<ByteArray> =
        componentGroupLists[componentGroupId]

    fun getMetadata(): TransactionMetaData {
        val mapper = jacksonObjectMapper()
        val metadataBytes = componentGroupLists[ALL_LEDGER_METADATA_COMPONENT_GROUP_ID].first()
        return mapper.readValue(metadataBytes, TransactionMetaData::class.java) // CORE-5940
    }

    fun getWrappedLedgerTransactionClassName(): String {
        return this.getMetadata().getLedgerModel()
    }

    private fun getDigestSetting(settingKey: String): Any {
        return this.getMetadata().getDigestSettings().get(settingKey)!!
    }

    private fun getRootMerkleTreeDigestProviderName() =
        getDigestSetting(ROOT_MERKLE_TREE_DIGEST_PROVIDER_NAME_KEY) as String

    private fun getRootMerkleTreeDigestAlgorithmName() =
        DigestAlgorithmName(getDigestSetting(ROOT_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY) as String)

    private fun getRootMerkleTreeDigestOptionsLeafPrefix() =
        Base64.getDecoder().decode(getDigestSetting(ROOT_MERKLE_TREE_DIGEST_OPTIONS_LEAF_PREFIX_B64_KEY) as String)

    private fun getRootMerkleTreeDigestOptionsNodePrefix() =
        Base64.getDecoder().decode(getDigestSetting(ROOT_MERKLE_TREE_DIGEST_OPTIONS_NODE_PREFIX_B64_KEY) as String)

    private fun getComponentMerkleTreeEntropyAlgorithmName() =
        DigestAlgorithmName(getDigestSetting(COMPONENT_MERKLE_TREE_ENTROPY_ALGORITHM_NAME_KEY) as String)

    private fun getComponentMerkleTreeDigestProviderName() =
        getDigestSetting(COMPONENT_MERKLE_TREE_DIGEST_PROVIDER_NAME_KEY) as String

    private fun getComponentMerkleTreeDigestAlgorithmName() =
        DigestAlgorithmName(getDigestSetting(COMPONENT_MERKLE_TREE_DIGEST_ALGORITHM_NAME_KEY) as String)

    private fun getRootMerkleTreeDigestProvider() : MerkleTreeHashDigestProvider = merkleTreeFactory.createHashDigestProvider(
        getRootMerkleTreeDigestProviderName(),
        getRootMerkleTreeDigestAlgorithmName(),
        mapOf(
            HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION to getRootMerkleTreeDigestOptionsLeafPrefix(),
            HASH_DIGEST_PROVIDER_NODE_PREFIX_OPTION to getRootMerkleTreeDigestOptionsNodePrefix()
        )
    )

    private fun getComponentGroupEntropy(
        privacySalt: PrivacySalt,
        componentGroupIndexBytes: ByteArray
    ): ByteArray =
        digestService.hash(
            concatByteArrays(privacySalt.bytes, componentGroupIndexBytes),
            getComponentMerkleTreeEntropyAlgorithmName()
        ).bytes

    private fun getComponentGroupMerkleTreeDigestProvider(
        privacySalt: PrivacySalt,
        componentGroupIndex: Int
    ) : MerkleTreeHashDigestProvider =
        merkleTreeFactory.createHashDigestProvider(
            getComponentMerkleTreeDigestProviderName(),
            getComponentMerkleTreeDigestAlgorithmName(),
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
