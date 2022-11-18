package net.corda.v5.ledger.utxo.transaction

import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.UtxoLedgerService
import java.io.Serializable

/**
 * [UtxoTransactionValidator] verifies a [UtxoLedgerTransaction].
 *
 * Implement [UtxoTransactionValidator] and pass the implementation into [UtxoLedgerService.receiveFinality] to perform
 * custom validation on the [UtxoLedgerTransaction] received from the initiator of finality.
 *
 * When validating a [UtxoLedgerTransaction] throw either an [IllegalArgumentException], [IllegalStateException] or
 * [CordaRuntimeException] to indicate that the transaction is invalid. This will lead to the termination of finality for the caller of
 * [UtxoLedgerService.receiveFinality] and all participants included in finalizing the transaction. Other exceptions will still stop
 * the progression of finality; however, the reason for the failure will not be communicated to the initiator of finality.
 *
 * @see UtxoLedgerService.receiveFinality
 */
fun interface UtxoTransactionValidator : Serializable {

    /**
     * Validate a [UtxoLedgerTransaction].
     *
     * Throw an [IllegalArgumentException], [IllegalStateException] or [CordaRuntimeException] to indicate that the transaction is invalid.
     *
     * @param ledgerTransaction The [UtxoLedgerTransaction] to validate.
     *
     * @throws Throwable If the [ledgerTransaction] fails validation.
     */
    @Suspendable
    fun checkTransaction(ledgerTransaction: UtxoLedgerTransaction)
}