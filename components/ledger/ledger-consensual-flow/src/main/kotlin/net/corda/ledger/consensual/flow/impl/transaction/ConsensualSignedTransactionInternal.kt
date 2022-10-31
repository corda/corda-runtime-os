package net.corda.ledger.consensual.flow.impl.transaction

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.consensual.data.transaction.ConsensualSignedTransactionContainer
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction

/**
 * This interface adds [WireTransaction] to interface [ConsensualSignedTransaction] so that there is possible conversion
 * to and from [ConsensualSignedTransactionContainer].
 */
interface ConsensualSignedTransactionInternal: ConsensualSignedTransaction {
    val wireTransaction: WireTransaction
}