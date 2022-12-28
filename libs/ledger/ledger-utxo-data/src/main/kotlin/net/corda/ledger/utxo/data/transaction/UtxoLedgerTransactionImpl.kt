package net.corda.ledger.utxo.data.transaction

import net.corda.ledger.utxo.data.state.filterIsContractStateInstance
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.Attachment
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TimeWindow
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import java.security.PublicKey

class UtxoLedgerTransactionImpl(
    private val wrappedWireTransaction: WrappedUtxoWireTransaction,
    override val inputStateAndRefs: List<StateAndRef<*>>,
    override val referenceStateAndRefs: List<StateAndRef<*>>
) : UtxoLedgerTransaction {

    override val id: SecureHash
        get() = wrappedWireTransaction.id

    override val timeWindow: TimeWindow
        get() = wrappedWireTransaction.timeWindow

    val attachmentIds: List<SecureHash>
        get() = wrappedWireTransaction.attachmentIds

    override val attachments: List<Attachment>
        get() = wrappedWireTransaction.attachments

    override val commands: List<Command>
        get() = wrappedWireTransaction.commands

    override val signatories: List<PublicKey>
        get() = wrappedWireTransaction.signatories

    override val inputStateRefs: List<StateRef>
        get() = wrappedWireTransaction.inputStateRefs

    override val referenceStateRefs: List<StateRef>
        get() = wrappedWireTransaction.referenceStateRefs

    override val outputStateAndRefs: List<StateAndRef<*>>
        get() = wrappedWireTransaction.outputStateAndRefs

    override fun getAttachment(id: SecureHash): Attachment {
        return attachments.singleOrNull { it.id == id }
            ?: throw IllegalArgumentException("Failed to find a single attachment with id: $id.")
    }

    override fun <T : Command> getCommands(type: Class<T>): List<T> {
        return commands.filterIsInstance(type)
    }

    override fun <T : ContractState> getInputStateAndRefs(type: Class<T>): List<StateAndRef<T>> {
        return inputStateAndRefs.filterIsContractStateInstance(type)
    }

    override fun <T : ContractState> getInputStates(type: Class<T>): List<T> {
        return inputContractStates.filterIsInstance(type)
    }

    override fun <T : ContractState> getReferenceStateAndRefs(type: Class<T>): List<StateAndRef<T>> {
        return referenceStateAndRefs.filterIsContractStateInstance(type)
    }

    override fun <T : ContractState> getReferenceStates(type: Class<T>): List<T> {
        return referenceContractStates.filterIsInstance(type)
    }

    override fun <T : ContractState> getOutputStateAndRefs(type: Class<T>): List<StateAndRef<T>> {
        return outputStateAndRefs.filterIsContractStateInstance(type)
    }

    override fun <T : ContractState> getOutputStates(type: Class<T>): List<T> {
        return outputContractStates.filterIsInstance(type)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UtxoLedgerTransactionImpl

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }

    override fun toString(): String {
        return "UtxoLedgerTransactionImpl(id=$id, wireTransaction=${wrappedWireTransaction.wireTransaction})"
    }
}
