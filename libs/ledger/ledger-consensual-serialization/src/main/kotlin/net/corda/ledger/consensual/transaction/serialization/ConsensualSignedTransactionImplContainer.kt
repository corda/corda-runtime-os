package net.corda.ledger.consensual.transaction.serialization

import net.corda.ledger.common.impl.transaction.WireTransaction
import net.corda.v5.ledger.common.transaction.TransactionSignature

/**
 * The class that actually gets serialized on the wire.
 */

data class ConsensualSignedTransactionImplContainer(
    /**
     * Version of container.
     */
    val version: ConsensualSignedTransactionImplVersion,

    /**
     * Properties for Consensual Signed transactions' serialisation.
     */
    val wireTransaction: WireTransaction,
    val signatures: List<TransactionSignature>
)
