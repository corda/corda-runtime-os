package net.corda.ledger.common.impl.transactions

import net.corda.crypto.core.concatByteArrays
import net.corda.crypto.core.toByteArray
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_ENTROPY_OPTION
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_LEAF_PREFIX_OPTION
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_NODE_PREFIX_OPTION
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_NONCE_NAME
import net.corda.v5.crypto.merkle.HASH_DIGEST_PROVIDER_TWEAKABLE_NAME
import net.corda.v5.crypto.merkle.MerkleTree
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import net.corda.v5.crypto.merkle.MerkleTreeHashDigestProvider
import net.corda.v5.ledger.common.transactions.LedgerTransaction
import net.corda.v5.ledger.common.transactions.PrivacySalt
import net.corda.v5.ledger.common.transactions.WireTransaction
import kotlin.reflect.KClass

internal const val ROOT_MERKLE_TREE_DIGEST_PROVIDER_NAME = HASH_DIGEST_PROVIDER_TWEAKABLE_NAME
internal val ROOT_MERKLE_TREE_DIGEST_ALGORITHM_NAME = DigestAlgorithmName.SHA2_256D

internal const val COMPONENT_MERKLE_TREE_DIGEST_PROVIDER_NAME = HASH_DIGEST_PROVIDER_NONCE_NAME
internal val COMPONENT_MERKLE_TREE_DIGEST_ALGORITHM_NAME = DigestAlgorithmName.SHA2_256D
internal val COMPONENT_MERKLE_TREE_ENTROPY_ALGORITHM_NAME = DigestAlgorithmName.SHA2_256D

class WireTransactionImpl(
    private val merkleTreeFactory: MerkleTreeFactory,
    private val digestService: DigestService,
    override val privacySalt: PrivacySalt,
    override val componentGroupLists: List<List<ByteArray>>
): WireTransaction{
    override val id: SecureHash by lazy(LazyThreadSafetyMode.PUBLICATION) {
        rootMerkleTree.root
    }

    override fun getComponentGroupList(componentGroupId: Int): List<ByteArray> =
        componentGroupLists[componentGroupId]

    override fun getWrapperLedgerTransactionClass(): String {
        // TODO(implement this)
        return "net.corda.ledger.consensual.impl.transactions.ConsensualLedgerTransactionImpl"
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


}
