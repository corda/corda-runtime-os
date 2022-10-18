package net.corda.ledger.utxo.impl.transaction

import net.corda.ledger.utxo.impl.state.filterIsContractStateInstance
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.Attachment
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.TimeWindow
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import java.security.PublicKey

data class UtxoLedgerTransactionImpl(
    override val timeWindow: TimeWindow,
    override val attachments: List<Attachment>,
    override val commands: List<Command>,
    override val signatories: List<PublicKey>,
    override val inputStateAndRefs: List<StateAndRef<*>>,
    override val referenceInputStateAndRefs: List<StateAndRef<*>>,
    override val outputStateAndRefs: List<StateAndRef<*>>
) : UtxoLedgerTransaction {

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

    override fun <T : ContractState> getReferenceInputStateAndRefs(type: Class<T>): List<StateAndRef<T>> {
        return referenceInputStateAndRefs.filterIsContractStateInstance(type)
    }

    override fun <T : ContractState> getReferenceInputStates(type: Class<T>): List<T> {
        return referenceInputContractStates.filterIsInstance(type)
    }

    override fun <T : ContractState> getOutputStateAndRefs(type: Class<T>): List<StateAndRef<T>> {
        return outputStateAndRefs.filterIsContractStateInstance(type)
    }

    override fun <T : ContractState> getOutputStates(type: Class<T>): List<T> {
        return outputContractStates.filterIsInstance(type)
    }

    override fun verify() {

    }
}
