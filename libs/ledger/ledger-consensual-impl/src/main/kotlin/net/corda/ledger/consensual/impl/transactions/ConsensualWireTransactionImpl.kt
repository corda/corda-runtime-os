package net.corda.ledger.consensual.impl.transactions

import net.corda.ledger.common.impl.transactions.CommonWireTransaction
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.merkle.MerkleTree
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import net.corda.v5.ledger.common.transactions.PrivacySalt
import net.corda.v5.ledger.consensual.transaction.ConsensualWireTransaction
import java.util.concurrent.ConcurrentHashMap

internal class ConsensualWireTransactionImpl :ConsensualWireTransaction, CommonWireTransaction
{
    constructor(
        merkleTreeFactory: MerkleTreeFactory,
        digestService: DigestService,

        privacySalt: PrivacySalt,

        metadata: ByteArray,
        timestamp: ByteArray,
        requiredSigners: List<ByteArray>,
        consensualStates: List<ByteArray>,
        consensualStateTypes: List<ByteArray>
    ) : super(merkleTreeFactory,
        digestService,
        privacySalt,
        calculateComponentGroupLists(
            metadata,
            timestamp,
            requiredSigners,
            consensualStates,
            consensualStateTypes
        )
    )
    override val metadata: ByteArray by lazy(LazyThreadSafetyMode.PUBLICATION) {
        componentGroupLists[ConsensualComponentGroups.METADATA.ordinal].first()
    }
    override val timestamp: ByteArray by lazy(LazyThreadSafetyMode.PUBLICATION) {
        componentGroupLists[ConsensualComponentGroups.TIMESTAMP.ordinal].first()
    }
    override val requiredSigners: List<ByteArray> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        componentGroupLists[ConsensualComponentGroups.REQUIRED_SIGNERS.ordinal]
    }
    override val consensualStates: List<ByteArray> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        componentGroupLists[ConsensualComponentGroups.OUTPUT_STATES.ordinal]
    }
    override val consensualStateTypes: List<ByteArray> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        componentGroupLists[ConsensualComponentGroups.OUTPUT_STATE_TYPES.ordinal]
    }

    companion object {
        private fun calculateComponentGroupLists(
            metadata: ByteArray,
            timestamp: ByteArray,
            requiredSigners: List<ByteArray>,
            consensualStates: List<ByteArray>,
            consensualStateTypes: List<ByteArray>
        ): List<List<ByteArray>> {
            // TODO(all the checks from te Tx Builder here too? Maybe remove the calculated arguments?Probably not since this may be called when it comes from DB. I guess. But at least the number of item in output states/state types should be the same)
            val componentGroupLists = mutableListOf<List<ByteArray>>()
            for (componentGroupIndex in ConsensualComponentGroups.values()) {
                componentGroupLists += when (componentGroupIndex) {
                    ConsensualComponentGroups.METADATA -> listOf(metadata)
                    ConsensualComponentGroups.TIMESTAMP -> listOf(timestamp)
                    ConsensualComponentGroups.REQUIRED_SIGNERS -> requiredSigners
                    ConsensualComponentGroups.OUTPUT_STATES -> consensualStates
                    ConsensualComponentGroups.OUTPUT_STATE_TYPES -> consensualStateTypes
                }
            }
            return componentGroupLists
        }
    }

    private val componentMerkleTrees = ConcurrentHashMap<ConsensualComponentGroups, MerkleTree>() // TODO(will we need this?)

}