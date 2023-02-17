package net.corda.ledger.utxo.data.transaction

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.utxo.data.state.StateAndRefImpl
import net.corda.ledger.utxo.data.state.TransactionStateImpl
import net.corda.ledger.utxo.data.state.getEncumbranceGroup
import net.corda.ledger.utxo.data.transaction.verifier.verifyMetadata
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.application.serialization.deserialize
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.utxo.Attachment
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TimeWindow
import java.security.PublicKey

class WrappedUtxoWireTransaction(
    val wireTransaction: WireTransaction,
    private val serializationService: SerializationService
) {

    companion object {
        const val notaryIndex: Int = 0
        const val timeWindowIndex: Int = 1
    }

    init{
        verifyMetadata(wireTransaction.metadata)
        check(wireTransaction.componentGroupLists[UtxoComponentGroup.OUTPUTS.ordinal].size ==
                wireTransaction.componentGroupLists[UtxoComponentGroup.OUTPUTS_INFO.ordinal].size
        ) {
            "The length of the outputs and output infos component groups needs to be the same."
        }
    }

    val id: SecureHash get() = wireTransaction.id

    val metadata: TransactionMetadata get() = wireTransaction.metadata

    val notary: Party by lazy(LazyThreadSafetyMode.PUBLICATION) {
        deserialize(UtxoComponentGroup.NOTARY, notaryIndex)
    }

    val timeWindow: TimeWindow by lazy(LazyThreadSafetyMode.PUBLICATION) {
        deserialize(UtxoComponentGroup.NOTARY, timeWindowIndex)
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

    val referenceStateRefs: List<StateRef> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        deserialize(UtxoComponentGroup.REFERENCES)
    }

    val outputStateAndRefs: List<StateAndRef<*>> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        wireTransaction
            .getComponentGroupList(UtxoComponentGroup.OUTPUTS.ordinal)
            .mapIndexed { index, state ->
                val contractState: ContractState = serializationService.deserialize(state)
                val stateRef = StateRef(id, index)
                val outputInfo = outputsInfo[index]
                val transactionState = TransactionStateImpl(contractState, outputInfo.notary, outputInfo.getEncumbranceGroup())
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
