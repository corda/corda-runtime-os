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

/**
 * Defines a marker interface which must be implemented by all [Contract] commands.
 */
@CordaSerializable
interface Command

/**
 * Defines a verifiable command; that is, a command that implements its own contract verification logic.
 */
interface VerifiableCommand : Command {

    /**
     * Verifies the specified transaction associated with the current contract command.
     *
     * @param transaction The transaction to verify.
     * @param signatories The signatories associated with the current contract command.
     */
    fun verify(transaction: UtxoLedgerTransaction, signatories: Iterable<PublicKey>)
}

/**
 * Defines a command, and the signatories associated with the specified command.
 *
 * @property command The command to verify.
 * @property signatories The signatory signing keys associated with the specified command.
 */
@CordaSerializable
interface CommandAndSignatories<out T : Command> {
    val command: T
    val signatories: Set<PublicKey>
}
