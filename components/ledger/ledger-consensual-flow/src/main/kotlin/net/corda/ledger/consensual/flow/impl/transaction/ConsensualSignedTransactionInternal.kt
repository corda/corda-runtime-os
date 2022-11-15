package net.corda.ledger.consensual.flow.impl.transaction

import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction

/**
 * This interface adds [WireTransaction] to interface [ConsensualSignedTransaction] so that there is possible conversion
 * to and from [SignedTransactionContainer].
 */
interface ConsensualSignedTransactionInternal: ConsensualSignedTransaction {
    val wireTransaction: WireTransaction
}
