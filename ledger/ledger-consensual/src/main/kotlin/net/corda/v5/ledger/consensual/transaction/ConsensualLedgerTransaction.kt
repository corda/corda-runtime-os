package net.corda.v5.ledger.consensual.transaction

import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.consensual.ConsensualState
import java.security.PublicKey
import java.time.Instant

/**
 * Defines a Consensual ledger transaction.
 *
 * Comparing with [ConsensualSignedTransaction]:
 *  - It has access to the deserialized details.
 *  - It does not have direct access to the signatures.
 *  - It requires a serializer.
 *
 * [ConsensualLedgerTransaction] is an abstraction that is meant to be used during the transaction verification stage.
 * It needs full access to its states that might be in transactions that are encrypted and unavailable for code running
 * outside the secure enclave.
 * Also, it might need to deserialize states with code that might not be available on the classpath.
 *
 * Because of this, trying to create or use a [ConsensualLedgerTransaction] for any other purpose then transaction
 * verification can result in unexpected exceptions, which need de be handled.
 *
 * [ConsensualLedgerTransaction]s should never be instantiated directly from client code, but rather via
 * net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction.toLedgerTransaction
 *
 */
@DoNotImplement
interface ConsensualLedgerTransaction {
    /**
     * @property id The ID of the transaction.
     */
    val id: SecureHash

    /**
     * @property requiredSigningKeys Set of [PublicKey] needed to make the underlying transaction valid.
     * Essentially the union of the participants of the transaction's [ConsensualState]s.
     */
    val requiredSigningKeys: Set<PublicKey>

    /**
     * @property timestamp The timestamp of the transaction. (When it got signed initially.)
     */
    val timestamp: Instant

    /**
     * @property states List of the output [ConsensualState]s of the transaction.
     */
    val states: List<ConsensualState>
}