package net.corda.ledger.utxo.data.transaction

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.utxo.data.state.StateAndRefImpl
import net.corda.ledger.utxo.data.state.TransactionStateImpl
import net.corda.ledger.utxo.data.state.filterIsContractStateInstance
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.application.serialization.deserialize
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.Attachment
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TimeWindow
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import java.security.PublicKey

data class UtxoLedgerTransactionImpl(
    private val wireTransaction: WireTransaction,
    private val serializationService: SerializationService
) : UtxoLedgerTransaction {

    override val id: SecureHash
        get() = wireTransaction.id

    override val timeWindow: TimeWindow by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val timeWindowBytes = wireTransaction.getComponentGroupList(UtxoComponentGroup.NOTARY.ordinal)[1]
        serializationService.deserialize(timeWindowBytes)
    }

    override val attachments: List<Attachment> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        wireTransaction
            .getComponentGroupList(UtxoComponentGroup.DATA_ATTACHMENTS.ordinal)
            .map { serializationService.deserialize(it) }
    }

    override val commands: List<Command> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        wireTransaction
            .getComponentGroupList(UtxoComponentGroup.COMMANDS.ordinal)
            .map { serializationService.deserialize(it) }
    }

    override val signatories: List<PublicKey> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        //TODO("Not yet implemented.")
        emptyList()
    }

    val inputStateRefs: List<StateRef> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        wireTransaction
            .getComponentGroupList(UtxoComponentGroup.INPUTS.ordinal)
            .map { serializationService.deserialize(it) }
    }

    override val inputStateAndRefs: List<StateAndRef<*>> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        //TODO("Not yet implemented.")
        emptyList()
    }

    val referenceInputStateRefs: List<StateRef> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        wireTransaction
            .getComponentGroupList(UtxoComponentGroup.REFERENCES.ordinal)
            .map { serializationService.deserialize(it) }
    }

    override val referenceInputStateAndRefs: List<StateAndRef<*>> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        //TODO("Not yet implemented.")
        emptyList()
    }

    private val outputsInfo: List<UtxoOutputInfoComponent> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        wireTransaction
            .getComponentGroupList(UtxoComponentGroup.OUTPUTS_INFO.ordinal)
            .map { serializationService.deserialize(it) }
    }

    override val outputStateAndRefs: List<StateAndRef<*>> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        wireTransaction
            .getComponentGroupList(UtxoComponentGroup.OUTPUTS.ordinal)
            .mapIndexed { index, state ->
                val contractState: ContractState = serializationService.deserialize(state)
                val stateRef = StateRef(id, index)
                val outputInfo = outputsInfo[index]
                val transactionState = TransactionStateImpl(contractState, outputInfo.notary, outputInfo.encumbrance)
                StateAndRefImpl(transactionState, stateRef)
            }
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
