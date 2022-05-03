package net.corda.v5.ledger.services

import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.transactions.SignedTransaction

@DoNotImplement
interface TransactionStorage {

    /**
     * Returns the [SignedTransaction] with the given [id], or null if no such transaction exists.
     *
     * @param id The [SecureHash] of the transaction to find.
     *
     * @return The retrieved [SignedTransaction] or null if it does not exist.
     */
    fun getTransaction(id: SecureHash): SignedTransaction?
}
