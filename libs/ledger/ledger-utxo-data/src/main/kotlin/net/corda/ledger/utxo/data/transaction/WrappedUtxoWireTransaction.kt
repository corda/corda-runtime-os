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

    private companion object {
        const val NOTARY_INDEX: Int = 0
        const val TIME_WINDOW_INDEX: Int = 1
    }

    val id: SecureHash get() = wireTransaction.id

    val notary: Party by lazy(LazyThreadSafetyMode.PUBLICATION) {
        deserialize(UtxoComponentGroup.NOTARY, NOTARY_INDEX)
    }

    val timeWindow: TimeWindow by lazy(LazyThreadSafetyMode.PUBLICATION) {
        deserialize(UtxoComponentGroup.NOTARY, TIME_WINDOW_INDEX)
    }

    val attachmentIds: List<SecureHash> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        deserialize(UtxoComponentGroup.DATA_ATTACHMENTS)
    }

    val attachments: List<Attachment> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        //TODO("Not yet implemented.")
        emptyList()
    }

    val commands: List<Command> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        deserialize(UtxoComponentGroup.COMMANDS)
    }

    val signatories: List<PublicKey> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        deserialize(UtxoComponentGroup.SIGNATORIES)
    }

    val inputStateRefs: List<StateRef> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        deserialize(UtxoComponentGroup.INPUTS)
    }

    val referenceInputStateRefs: List<StateRef> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        deserialize(UtxoComponentGroup.REFERENCES)
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

    private val outputsInfo: List<UtxoOutputInfoComponent> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        deserialize(UtxoComponentGroup.OUTPUTS_INFO)
    }

    private inline fun <reified T> deserialize(group: UtxoComponentGroup): List<T> {
        return wireTransaction.getComponentGroupList(group.ordinal).map { serializationService.deserialize(it) }
    }

    private inline fun <reified T> deserialize(group: UtxoComponentGroup, index: Int): T {
        val serializedBytes = wireTransaction.getComponentGroupList(group.ordinal)[index]
        return serializationService.deserialize(serializedBytes)
    }
}
