package net.corda.ledger.utxo.impl.transaction

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.utxo.impl.state.filterIsContractStateInstance
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.Attachment
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.TimeWindow
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import java.security.PublicKey

data class UtxoLedgerTransactionImpl(
    private val wireTransaction: WireTransaction,
    private val serializationService: SerializationService
) : UtxoLedgerTransaction {

    override val timeWindow: TimeWindow by lazy(LazyThreadSafetyMode.PUBLICATION) {
        TODO("Not yet implemented.")
    }

    override val attachments: List<Attachment> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        TODO("Not yet implemented.")
    }

    override val commands: List<Command> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        TODO("Not yet implemented.")
    }

    override val signatories: List<PublicKey> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        TODO("Not yet implemented.")
    }

    override val inputStateAndRefs: List<StateAndRef<*>> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        TODO("Not yet implemented.")
    }

    override val referenceInputStateAndRefs: List<StateAndRef<*>> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        TODO("Not yet implemented.")
    }

    override val outputStateAndRefs: List<StateAndRef<*>> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        TODO("Not yet implemented.")
    }

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
        TODO("Not yet implemented.")
    }
}
