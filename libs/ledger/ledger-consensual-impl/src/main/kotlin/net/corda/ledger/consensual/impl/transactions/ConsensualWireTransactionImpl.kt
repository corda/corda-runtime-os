package net.corda.ledger.consensual.impl.transactions

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
import net.corda.v5.ledger.common.transactions.PrivacySalt
import net.corda.v5.ledger.consensual.transaction.ConsensualWireTransaction
import java.util.concurrent.ConcurrentHashMap

internal const val ROOT_MERKLE_TREE_DIGEST_PROVIDER_NAME = HASH_DIGEST_PROVIDER_TWEAKABLE_NAME
internal val ROOT_MERKLE_TREE_DIGEST_ALGORITHM_NAME = DigestAlgorithmName.SHA2_256D

internal const val COMPONENT_MERKLE_TREE_DIGEST_PROVIDER_NAME = HASH_DIGEST_PROVIDER_NONCE_NAME
internal val COMPONENT_MERKLE_TREE_DIGEST_ALGORITHM_NAME = DigestAlgorithmName.SHA2_256D
internal val COMPONENT_MERKLE_TREE_ENTROPY_ALGORITHM_NAME = DigestAlgorithmName.SHA2_256D

internal class ConsensualWireTransactionImpl
    internal constructor(
        private val merkleTreeFactory: MerkleTreeFactory,
        private val digestService: DigestService,

        override val privacySalt: PrivacySalt,

        override val metadata: ByteArray,
        override val timestamp: ByteArray,
        override val requiredSigners: List<ByteArray>,
        override val consensualStates: List<ByteArray>,
        override val consensualStateTypes: List<ByteArray>,
    ): ConsensualWireTransaction {

    override val id: SecureHash by lazy(LazyThreadSafetyMode.PUBLICATION) {
        rootMerkleTree.root
    }

    private val componentMerkleTrees = ConcurrentHashMap<ConsensualComponentGroups, MerkleTree>()

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

    private fun getComponentMerkleTreeDigestProvider(
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

    private fun getComponentList(component: ConsensualComponentGroups): List<ByteArray> {
        return when (component) {
            ConsensualComponentGroups.METADATA -> listOf(metadata)
            ConsensualComponentGroups.TIMESTAMP -> listOf(timestamp)
            ConsensualComponentGroups.REQUIRED_SIGNERS -> requiredSigners
            ConsensualComponentGroups.OUTPUT_STATES -> consensualStates
            ConsensualComponentGroups.OUTPUT_STATE_TYPES -> consensualStateTypes
        }
    }

    private val rootMerkleTree: MerkleTree by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val componentsList = mutableListOf<ByteArray>()
        for (componentGroup in ConsensualComponentGroups.values()) {
            val leaves = getComponentList(componentGroup)
            val componentMerkleTree = merkleTreeFactory.createTree(
                leaves,
                getComponentMerkleTreeDigestProvider(privacySalt, componentGroup.ordinal)
            )
            componentMerkleTrees[componentGroup] = componentMerkleTree
            componentsList += componentMerkleTree.root.bytes
        }
        merkleTreeFactory.createTree(componentsList, getRootMerkleTreeDigestProvider())
    }
}
