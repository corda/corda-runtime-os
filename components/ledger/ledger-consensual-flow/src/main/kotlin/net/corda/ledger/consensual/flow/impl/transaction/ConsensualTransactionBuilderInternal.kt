package net.corda.ledger.consensual.flow.impl.transaction

import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.common.transaction.TransactionNoAvailableKeysException
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder

/**
 * Internal representation of [ConsensualTransactionBuilder] for methods we don't want used publicly.
 */
interface ConsensualTransactionBuilderInternal : ConsensualTransactionBuilder {

    /**
     * Verifies the content of the [ConsensualTransactionBuilder] and
     * signs the transaction with any required signatories that belong to the current node.
     *
     * Calling this function once consumes the [ConsensualTransactionBuilder], so it cannot be used again.
     * Therefore, if you want to build two transactions you need two builders.
     *
     * @return Returns a [ConsensualSignedTransactionInternal] with signatures for any required signatories that belong to the current node.
     *
     * @throws IllegalStateException when called a second time on the same object to prevent
     *      unintentional duplicate transactions.
     * @throws TransactionNoAvailableKeysException if none of the required keys are available to sign the transaction.
     */
    @Suspendable
    fun toSignedTransaction(): ConsensualSignedTransactionInternal
}