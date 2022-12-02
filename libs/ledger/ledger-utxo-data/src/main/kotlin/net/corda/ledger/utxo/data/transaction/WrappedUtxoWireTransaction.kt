package net.corda.ledger.utxo.data.transaction

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.utxo.data.state.StateAndRefImpl
import net.corda.ledger.utxo.data.state.TransactionStateImpl
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.application.serialization.deserialize
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.Attachment
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TimeWindow
import java.security.PublicKey

class WrappedUtxoWireTransaction(
    private val wireTransaction: WireTransaction,
    private val serializationService: SerializationService
) {
    companion object {
        private const val notaryIndex: Int = 0
        private const val timeWindowIndex: Int = 1
    }
    val id: SecureHash
        get() = wireTransaction.id

    val notary: Party by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val notaryBytes = wireTransaction.getComponentGroupList(UtxoComponentGroup.NOTARY.ordinal)[notaryIndex]
        serializationService.deserialize(notaryBytes)
    }

    val timeWindow: TimeWindow by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val timeWindowBytes = wireTransaction.getComponentGroupList(UtxoComponentGroup.NOTARY.ordinal)[timeWindowIndex]
        serializationService.deserialize(timeWindowBytes)
    }

    val attachmentIds: List<SecureHash> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        wireTransaction
            .getComponentGroupList(UtxoComponentGroup.DATA_ATTACHMENTS.ordinal)
            .map { serializationService.deserialize(it) }
    }

    val attachments: List<Attachment> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        //TODO("Not yet implemented.")
        emptyList()
    }

    val commands: List<Command> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        wireTransaction
            .getComponentGroupList(UtxoComponentGroup.COMMANDS.ordinal)
            .map { serializationService.deserialize(it) }
    }

    val signatories: List<PublicKey> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        wireTransaction
            .getComponentGroupList(UtxoComponentGroup.SIGNATORIES.ordinal)
            .map { serializationService.deserialize(it) }
    }

    val inputStateRefs: List<StateRef> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        wireTransaction
            .getComponentGroupList(UtxoComponentGroup.INPUTS.ordinal)
            .map { serializationService.deserialize(it) }
    }

    val referenceInputStateRefs: List<StateRef> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        wireTransaction
            .getComponentGroupList(UtxoComponentGroup.REFERENCES.ordinal)
            .map { serializationService.deserialize(it) }
    }

    private val outputsInfo: List<UtxoOutputInfoComponent> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        wireTransaction
            .getComponentGroupList(UtxoComponentGroup.OUTPUTS_INFO.ordinal)
            .map { serializationService.deserialize(it) }
    }

    val outputStateAndRefs: List<StateAndRef<*>> by lazy(LazyThreadSafetyMode.PUBLICATION) {
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
}