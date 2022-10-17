package net.corda.v5.ledger.consensual

import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransactionVerifier
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder

/**
 * It provides access to the different Consensual Ledger Services
 */
@DoNotImplement
interface ConsensualLedgerService {
    /**
     * Returns an empty [ConsensualTransactionBuilder] instance
     */
    @Suspendable
    fun getTransactionBuilder(): ConsensualTransactionBuilder

    /* TODO
    fun fetchTransaction(id: SecureHash)
    ... Vault ...
    */

    /**
     * Collects signatures, records and broadcasts to involved peers a [ConsensualSignedTransaction].
     *
     * @param signedTransaction The [ConsensualSignedTransaction] to finalise and recorded locally and with peer [sessions].
     * @param sessions The [FlowSession]s of the peers involved in the transaction.
     *
     * @return The fully signed [ConsensualSignedTransaction] that was recorded.
     */
    @Suspendable
    fun finality(
        signedTransaction: ConsensualSignedTransaction,
        sessions: List<FlowSession>
    ): ConsensualSignedTransaction


    /**
     * Verifies, signs and records a [ConsensualSignedTransaction].
     *
     * @param session The [FlowSession] to receive the [ConsensualSignedTransaction] from.
     * @param verifier Verifies the received [ConsensualSignedTransaction].
     *
     * @return The fully signed [ConsensualSignedTransaction] that was received and recorded.
     */
    @Suspendable
    fun receiveFinality(
        session: FlowSession,
        verifier: ConsensualSignedTransactionVerifier
    ): ConsensualSignedTransaction
}