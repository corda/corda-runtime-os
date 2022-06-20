package net.corda.v5.ledger.obsolete.services

import net.corda.v5.ledger.obsolete.contracts.ContractState
import net.corda.v5.ledger.obsolete.contracts.OwnableState

/**
 * Controls whether the transaction is sent to the vault at all, and if so whether states have to be relevant
 * or not in order to be recorded. Used in [TransactionService.record]
 */
enum class StatesToRecord {
    /** The received transaction is not sent to the vault at all. This is used within transaction resolution. */
    NONE,
    /**
     * All states that can be seen in the transaction will be recorded by the vault, even if none of the identities
     * on this node are a participant or owner.
     */
    ALL_VISIBLE,
    /**
     * Only states that involve one of our public keys will be stored in the vault. This is the default. A public
     * key is involved (relevant) if it's in the [OwnableState.owner] field, or appears in the [ContractState.participants]
     * collection. This is usually equivalent to "can I change the contents of this state by signing a transaction".
     */
    ONLY_RELEVANT
}
