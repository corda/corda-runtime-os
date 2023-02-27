package net.corda.ledger.utxo.flow.impl.transaction.filtered

import net.corda.ledger.common.flow.transaction.filtered.FilteredTransaction
import net.corda.ledger.utxo.data.state.StateAndRefImpl
import net.corda.ledger.utxo.data.state.TransactionStateImpl
import net.corda.ledger.utxo.data.state.getEncumbranceGroup
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.ledger.utxo.data.transaction.UtxoOutputInfoComponent
import net.corda.ledger.utxo.data.transaction.WrappedUtxoWireTransaction
import net.corda.utilities.serialization.deserialize
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.merkle.MerkleProofType
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TimeWindow
import net.corda.v5.ledger.utxo.transaction.filtered.FilteredDataInconsistencyException
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredData
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransaction
import java.security.PublicKey

@Suppress("TooManyFunctions")
class UtxoFilteredTransactionImpl(
    private val serializationService: SerializationService,
    val filteredTransaction: FilteredTransaction
) : UtxoFilteredTransaction {

    override fun getId(): SecureHash {
        return filteredTransaction.id
    }

    override fun getMetadata(): TransactionMetadata {
        return filteredTransaction.metadata
    }

    override fun getTimeWindow(): TimeWindow? {
        return filteredTransaction.getComponentGroupContent(UtxoComponentGroup.NOTARY.ordinal)
            ?.singleOrNull { it.first == WrappedUtxoWireTransaction.timeWindowIndex }
            ?.let { serializationService.deserialize(it.second, TimeWindow::class.java) }
    }

    override fun getNotary(): Party? {
        return filteredTransaction.getComponentGroupContent(UtxoComponentGroup.NOTARY.ordinal)
            ?.singleOrNull { it.first == WrappedUtxoWireTransaction.notaryIndex }
            ?.let { serializationService.deserialize(it.second, Party::class.java) }
    }

    override fun getSignatories(): UtxoFilteredData<PublicKey> {
        return getFilteredData(UtxoComponentGroup.SIGNATORIES.ordinal)
    }

    override fun getCommands(): UtxoFilteredData<Command> {
        return getFilteredData(UtxoComponentGroup.COMMANDS.ordinal)
    }

    override fun getInputStateRefs(): UtxoFilteredData<StateRef> {
        return getFilteredData(UtxoComponentGroup.INPUTS.ordinal)
    }

    override fun getReferenceStateRefs(): UtxoFilteredData<StateRef> {
        return getFilteredData(UtxoComponentGroup.REFERENCES.ordinal)
    }

    override fun getOutputStateAndRefs(): UtxoFilteredData<StateAndRef<*>> {
        return getFilteredData<ContractState>(UtxoComponentGroup.OUTPUTS.ordinal).let { filteredOutputStates ->
            when (filteredOutputStates) {
                is UtxoFilteredData.Removed<ContractState> -> FilteredDataRemovedImpl()
                is UtxoFilteredData.SizeOnly -> FilteredDataSizeImpl(filteredOutputStates.size)
                is UtxoFilteredData.Audit -> extractOutputStateAndRefs(filteredOutputStates)
                else -> throw FilteredDataInconsistencyException("Unknown filtered data type.")
            }
        }
    }

    private fun extractOutputStateAndRefs(
        filteredOutputStates: UtxoFilteredData.Audit<ContractState>
    ): UtxoFilteredData<StateAndRef<*>> {
        val componentGroupOrdinal = UtxoComponentGroup.OUTPUTS_INFO.ordinal
        return when (val filteredStateInfos = getFilteredData<UtxoOutputInfoComponent>(componentGroupOrdinal)) {
            is UtxoFilteredData.Audit -> {
                val values = filteredOutputStates.values.entries.associateBy(
                    keySelector = { (key, _) -> key },
                    valueTransform = { (key, value) ->
                        val info = filteredStateInfos.values[key]
                            ?: throw FilteredDataInconsistencyException("Missing output info")
                        StateAndRefImpl(
                            TransactionStateImpl(value, info.notary, info.getEncumbranceGroup()),
                            StateRef(id, key)
                        )
                    })
                FilteredDataAuditImpl(filteredOutputStates.size, values)
            }

            else -> {
                if (filteredOutputStates.size == 0) FilteredDataSizeImpl(0)
                else throw FilteredDataInconsistencyException(
                    "Output infos have been removed. Cannot reconstruct outputs"
                )
            }
        }
    }

    override fun verify() {
        filteredTransaction.verify()
    }

    private class FilteredDataRemovedImpl<T> : UtxoFilteredData.Removed<T>

    private class FilteredDataSizeImpl<T>(private val size: Int) : UtxoFilteredData.SizeOnly<T> {

        override fun getSize(): Int {
            return size
        }
    }

    private class FilteredDataAuditImpl<T>(
        private val size: Int,
        private val values: Map<Int, T>
    ) : UtxoFilteredData.Audit<T> {

        override fun getSize(): Int {
            return size
        }

        override fun getValues(): Map<Int, T> {
            return values
        }
    }

    private inline fun <reified T : Any> getFilteredData(index: Int): UtxoFilteredData<T> {
        return filteredTransaction.filteredComponentGroups[index]?.let { group ->
            when (group.merkleProof.proofType) {
                MerkleProofType.SIZE -> return FilteredDataSizeImpl(group.merkleProof.treeSize)
                MerkleProofType.AUDIT -> {
                    // if it's an audit proof of an empty list, we need to strip the marker
                    return if (group.merkleProof.leaves.size == 1
                        && group.merkleProof.leaves.first().leafData.isEmpty()
                    ) FilteredDataAuditImpl(0, emptyMap())
                    else FilteredDataAuditImpl(
                        group.merkleProof.treeSize,
                        group.merkleProof.leaves.associateBy({ leaf -> leaf.index }, { leaf ->
                            serializationService.deserialize(leaf.leafData)
                        })
                    )
                }
            }
        } ?: return FilteredDataRemovedImpl()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UtxoFilteredTransactionImpl

        if (filteredTransaction != other.filteredTransaction) return false

        return true
    }

    override fun hashCode(): Int {
        return filteredTransaction.hashCode()
    }

    override fun toString(): String {
        return "UtxoFilteredTransactionImpl(filteredTransaction=$filteredTransaction)"
    }
}
