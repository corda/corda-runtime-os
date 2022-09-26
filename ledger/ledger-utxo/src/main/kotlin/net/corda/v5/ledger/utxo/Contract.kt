package net.corda.v5.ledger.utxo

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import java.security.PublicKey

/**
 * Defines a mechanism for implementing contracts, which perform transaction verification.
 *
 * All participants run this code for every input and output state for every transaction they see on the network.
 * All contracts must verify and accept the associated transaction for it to be finalized and persisted to the ledger.
 * Failure of any contract constraint aborts the transaction, resulting in the transaction not being finalized.
 */
@CordaSerializable
interface Contract {

    /**
     * Verifies the specified transaction associated with the current contract.
     *
     * @param transaction The transaction to verify.
     */
    fun verify(transaction: UtxoLedgerTransaction)
}

