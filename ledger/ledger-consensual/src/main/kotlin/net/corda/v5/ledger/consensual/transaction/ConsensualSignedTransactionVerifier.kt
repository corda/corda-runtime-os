package net.corda.v5.ledger.consensual.transaction

import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.consensual.ConsensualLedgerService
import java.io.Serializable

/**
 * [ConsensualSignedTransactionVerifier] verifies a [ConsensualSignedTransaction].
 *
 * Implement [ConsensualSignedTransactionVerifier] and pass the implementation into [ConsensualLedgerService.receiveFinality] to perform
 * custom verification on the [ConsensualSignedTransaction] received from the initiator of finality.
 *
 * When verifying a [ConsensualSignedTransaction] throw either an [IllegalArgumentException], [IllegalStateException] or
 * [CordaRuntimeException] to indicate that the transaction is invalid. This will lead to the termination of finality for the caller of
 * [ConsensualLedgerService.receiveFinality] and all participants included in finalizing the transaction. Other exceptions will still stop
 * the progression of finality; however, the reason for the failure will not be communicated to the initiator of finality.
 *
 * @see ConsensualLedgerService.receiveFinality
 */
fun interface ConsensualSignedTransactionVerifier : Serializable {

    /**
     * Verify a [ConsensualSignedTransaction].
     *
     * Throw an [IllegalArgumentException], [IllegalStateException] or [CordaRuntimeException] to indicate that the transaction is invalid.
     *
     * @param signedTransaction The [ConsensualSignedTransaction] to verify.
     *
     * @throws Throwable If the [signedTransaction] fails verification.
     */
    @Suspendable
    fun verify(signedTransaction: ConsensualSignedTransaction)
}