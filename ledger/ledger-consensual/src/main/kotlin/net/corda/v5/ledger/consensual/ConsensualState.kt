package net.corda.v5.ledger.consensual

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import java.security.PublicKey

/**
 * A consensual state (or just "state") contains opaque data used by a consensual ledger. It can be thought of as a disk
 * file that the program can use to persist data across transactions. ConsensualState are immutable: once created they are never
 * updated, instead, any changes must generate a new successor state.
 * Consensual states cannot be consumed.
 */
@CordaSerializable
interface ConsensualState {
    /**
     * @property participants A _participant_ is any party whose consent is needed to make a Consensual State valid and final.
     *
     * Participants are the main and only verification points for Consensual state since they do not have contract code.
     * Every participant has to be involved and approve the transaction
     * so that they receive the updated state, and don't end up in a situation where they can no longer use a state
     * they possess.
     *
     * The participants list should normally be derived from the contents of the state.
     */
    val participants: List<PublicKey>

    /**
     * An override of this function needs to be provided to:
     *  - verify the state's well-formedness
     *  - verify compatibility with the other states of the encapsulating transaction
     *  - check required signing keys
     *  - check the transaction's timestamp.
     *
     * TODO(make services injectable (crypto, etc... CORE-5995)
     *
     * @param ledgerTransaction encapsulating transaction
     *
     */
    fun verify(ledgerTransaction: ConsensualLedgerTransaction)
}
