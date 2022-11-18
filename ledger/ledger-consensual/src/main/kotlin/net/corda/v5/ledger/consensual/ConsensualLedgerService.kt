package net.corda.v5.ledger.consensual

import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionValidator
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder

/**
 * It provides access to the different Consensual Ledger Services
 */
@DoNotImplement
interface ConsensualLedgerService {
    /**
     * Gets a Consensual transaction builder.
     *
     * @return Returns a new [ConsensualTransactionBuilder] instance.
     */
    @Suspendable
    fun getTransactionBuilder(): ConsensualTransactionBuilder

    /**
     * Finds a transaction by id in the vault and returns it
     *
     * @param id The id of the transaction to find
     *
     * @return The signed transaction, if it has been recorded previously. Null if not found.
     */
    @Suspendable
    fun findSignedTransaction(id: SecureHash): ConsensualSignedTransaction?

    /**
     * Finds a transaction by id in the vault, resolves it to a ledger transaction and returns it
     *
     * @param id The id of the transaction to find
     *
     * @return The ledger transaction, if it has been recorded previously. Null if not found.
     */
    @Suspendable
    fun findLedgerTransaction(id: SecureHash): ConsensualLedgerTransaction?

    /**
     * Collects signatures, records and broadcasts to involved peers a [ConsensualSignedTransaction].
     *
     * @param signedTransaction The [ConsensualSignedTransaction] to finalise and recorded locally and with peer [sessions].
     * @param sessions The [FlowSession]s of the peers involved in the transaction.
     *
     * @return The fully signed [ConsensualSignedTransaction] that was recorded.
     */
    @Suspendable
    fun finalize(
        signedTransaction: ConsensualSignedTransaction,
        sessions: List<FlowSession>
    ): ConsensualSignedTransaction

    /**
     * Verifies, signs and records a [ConsensualSignedTransaction].
     *
     * @param session The [FlowSession] to receive the [ConsensualSignedTransaction] from.
     * @param validator Validates the received [ConsensualSignedTransaction].
     *
     * @return The fully signed [ConsensualSignedTransaction] that was received and recorded.
     */
    @Suspendable
    fun receiveFinality(
        session: FlowSession,
        validator: ConsensualTransactionValidator
    ): ConsensualSignedTransaction
}