package net.corda.v5.ledger.consensual.transaction

import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.consensual.ConsensualLedgerService
import java.io.Serializable

/**
 * [ConsensualTransactionValidator] verifies a [ConsensualLedgerTransaction].
 *
 * Implement [ConsensualTransactionValidator] and pass the implementation into [ConsensualLedgerService.receiveFinality] to perform
 * custom validation on the [ConsensualLedgerTransaction] received from the initiator of finality.
 *
 * When validating a [ConsensualLedgerTransaction] throw either an [IllegalArgumentException], [IllegalStateException] or
 * [CordaRuntimeException] to indicate that the transaction is invalid. This will lead to the termination of finality for the caller of
 * [ConsensualLedgerService.receiveFinality] and all participants included in finalizing the transaction. Other exceptions will still stop
 * the progression of finality; however, the reason for the failure will not be communicated to the initiator of finality.
 *
 * @see ConsensualLedgerService.receiveFinality
 */
fun interface ConsensualTransactionValidator : Serializable {

    /**
     * Validate a [ConsensualLedgerTransaction].
     *
     * Throw an [IllegalArgumentException], [IllegalStateException] or [CordaRuntimeException] to indicate that the transaction is invalid.
     *
     * @param ledgerTransaction The [ConsensualLedgerTransaction] to validate.
     *
     * @throws Throwable If the [ledgerTransaction] fails validation.
     */
    @Suspendable
    fun checkTransaction(ledgerTransaction: ConsensualLedgerTransaction)
}