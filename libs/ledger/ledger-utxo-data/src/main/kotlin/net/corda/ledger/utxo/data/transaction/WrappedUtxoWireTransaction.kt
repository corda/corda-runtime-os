package net.corda.ledger.utxo.data.transaction

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.utxo.data.transaction.verifier.verifyMetadata
import net.corda.utilities.serialization.deserialize
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.TransactionMetadata
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
        const val notaryNameIndex: Int = 0
        const val notaryKeyIndex: Int = 1
        const val timeWindowIndex: Int = 2
    }

    init {
        verifyMetadata(wireTransaction.metadata)
        check(
            wireTransaction.componentGroupLists[UtxoComponentGroup.OUTPUTS.ordinal].size ==
                wireTransaction.componentGroupLists[UtxoComponentGroup.OUTPUTS_INFO.ordinal].size
        ) {
            "The length of the outputs and output infos component groups needs to be the same."
        }
    }

    val id: SecureHash get() = wireTransaction.id

    val metadata: TransactionMetadata get() = wireTransaction.metadata

    val notaryName: MemberX500Name by lazy(LazyThreadSafetyMode.PUBLICATION) {
        deserialize(UtxoComponentGroup.NOTARY, notaryNameIndex)
    }

    val notaryKey: PublicKey by lazy(LazyThreadSafetyMode.PUBLICATION) {
        deserialize(UtxoComponentGroup.NOTARY, notaryKeyIndex)
    }

    val timeWindow: TimeWindow by lazy(LazyThreadSafetyMode.PUBLICATION) {
        deserialize(UtxoComponentGroup.NOTARY, timeWindowIndex)
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
        List(
            wireTransaction
                .getComponentGroupList(UtxoComponentGroup.OUTPUTS.ordinal).size
        ) { index ->
            UtxoVisibleTransactionOutputDto(
                id.toString(),
                index,
                wireTransaction
                    .getComponentGroupList(UtxoComponentGroup.OUTPUTS_INFO.ordinal)[index],
                wireTransaction
                    .getComponentGroupList(UtxoComponentGroup.OUTPUTS.ordinal)[index]
            ).toStateAndRef<ContractState>(serializationService)
        }
    }

    val dependencies: Set<SecureHash>
        get() = this
            .let { it.inputStateRefs.asSequence() + it.referenceStateRefs.asSequence() }
            .map { it.transactionId }
            .toSet()

    private inline fun <reified T> deserialize(group: UtxoComponentGroup): List<T> {
        return wireTransaction.getComponentGroupList(group.ordinal).map { serializationService.deserialize(it) }
    }

    private inline fun <reified T> deserialize(group: UtxoComponentGroup, index: Int): T {
        val serializedBytes = wireTransaction.getComponentGroupList(group.ordinal)[index]
        return serializationService.deserialize(serializedBytes)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as WrappedUtxoWireTransaction

        return wireTransaction == other.wireTransaction
    }

    override fun hashCode(): Int {
        return wireTransaction.hashCode()
    }

    override fun toString(): String {
        return "WrappedUtxoWireTransaction(wireTransaction=$wireTransaction)"
    }
}
