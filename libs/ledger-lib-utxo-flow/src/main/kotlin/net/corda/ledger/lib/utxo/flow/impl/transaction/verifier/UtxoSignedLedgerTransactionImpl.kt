package net.corda.ledger.lib.utxo.flow.impl.transaction.verifier

import net.corda.ledger.lib.utxo.flow.impl.transaction.UtxoSignedLedgerTransaction
import net.corda.ledger.lib.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionInternal
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TimeWindow
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import java.security.PublicKey

/**
 * [UtxoSignedLedgerTransactionImpl] delegates to [UtxoLedgerTransactionInternal] and [UtxoSignedTransactionInternal] instances to provide
 * the behaviour of the two interfaces.
 *
 * All the overridden methods in this class are methods that appear on both [UtxoLedgerTransactionInternal] and
 * [UtxoSignedTransactionInternal]. The implementation from the [signedTransaction] is used; however, using the [ledgerTransaction]
 * instead will lead to the same behaviour.
 */
data class UtxoSignedLedgerTransactionImpl(
    override val ledgerTransaction: UtxoLedgerTransactionInternal,
    override val signedTransaction: UtxoSignedTransactionInternal
) : UtxoSignedLedgerTransaction, UtxoLedgerTransaction by ledgerTransaction, UtxoSignedTransactionInternal by signedTransaction {

    override fun getId(): SecureHash {
        return signedTransaction.id
    }

    override fun getMetadata(): TransactionMetadata {
        return signedTransaction.metadata
    }

    override fun getInputStateRefs(): MutableList<StateRef> {
        return signedTransaction.inputStateRefs
    }

    override fun getReferenceStateRefs(): MutableList<StateRef> {
        return signedTransaction.referenceStateRefs
    }

    override fun getOutputStateAndRefs(): MutableList<StateAndRef<*>> {
        return signedTransaction.outputStateAndRefs
    }

    override fun getNotaryName(): MemberX500Name {
        return signedTransaction.notaryName
    }

    override fun getNotaryKey(): PublicKey {
        return signedTransaction.notaryKey
    }

    override fun getTimeWindow(): TimeWindow {
        return signedTransaction.timeWindow
    }

    override fun getCommands(): MutableList<Command> {
        return signedTransaction.commands
    }

    override fun getSignatories(): MutableList<PublicKey> {
        return signedTransaction.signatories
    }
}
