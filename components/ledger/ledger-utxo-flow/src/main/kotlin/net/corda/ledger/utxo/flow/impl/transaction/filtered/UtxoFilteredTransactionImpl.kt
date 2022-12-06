package net.corda.ledger.utxo.flow.impl.transaction.filtered

import net.corda.ledger.common.flow.transaction.filtered.FilteredTransaction
import net.corda.ledger.utxo.data.state.StateAndRefImpl
import net.corda.ledger.utxo.data.state.TransactionStateImpl
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.ledger.utxo.data.transaction.UtxoOutputInfoComponent
import net.corda.ledger.utxo.data.transaction.WrappedUtxoWireTransaction
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
import net.corda.v5.ledger.utxo.transaction.FilteredDataInconsistencyException
import net.corda.v5.ledger.utxo.transaction.UtxoFilteredData
import net.corda.v5.ledger.utxo.transaction.UtxoFilteredTransaction
import java.security.PublicKey

class UtxoFilteredTransactionImpl(
    private val serializationService: SerializationService,
    val filteredTransaction: FilteredTransaction
) : UtxoFilteredTransaction {
    override val commands: UtxoFilteredData<Command>
        get() = fetchFilteredData(UtxoComponentGroup.COMMANDS.ordinal)
    override val id: SecureHash
        get() = filteredTransaction.id
    override val inputStateRefs: UtxoFilteredData<StateRef>
        get() = fetchFilteredData(UtxoComponentGroup.INPUTS.ordinal)
    override val metadata: TransactionMetadata
        get() = filteredTransaction.metadata
    override val notary: Party?
        get() = filteredTransaction
            .getComponentGroupContent(UtxoComponentGroup.NOTARY.ordinal)
            ?.singleOrNull { it.first == WrappedUtxoWireTransaction.notaryIndex }
            ?.let { serializationService.deserialize(it.second, Party::class.java) }

    override val outputStateAndRefs: UtxoFilteredData<StateAndRef<*>>
        get() = fetchFilteredData<ContractState>(UtxoComponentGroup.OUTPUTS.ordinal).let {
            when (it) {
                is UtxoFilteredData.Removed<ContractState> -> FilteredDataRemovedImpl()
                is UtxoFilteredData.SizeOnly -> FilteredDataSizeImpl(it.size)
                is UtxoFilteredData.Audit -> {
                    when (val stateInfos =
                        fetchFilteredData<UtxoOutputInfoComponent>(UtxoComponentGroup.OUTPUTS_INFO.ordinal)) {
                        is UtxoFilteredData.Audit -> {
                            val values = it.values.entries.associateBy(
                                keySelector = { entry -> entry.key },
                                valueTransform = { entry ->
                                    val info = stateInfos.values.getOrDefault(entry.key, null)
                                        ?: throw FilteredDataInconsistencyException("Missing output info")
                                    val txState =
                                        TransactionStateImpl(entry.value, info.notary, info.encumbrance)
                                    StateAndRefImpl(txState, StateRef(id, entry.key))
                                }
                            )
                            FilteredDataAuditImpl(it.size, values)
                        }

                        else ->
                            throw FilteredDataInconsistencyException("Output infos have been removed. Cannot reconstruct outputs")
                    }
                }

                else ->
                    throw FilteredDataInconsistencyException("Unknown filtered data type.")
            }
        }

    override val referenceInputStateRefs: UtxoFilteredData<StateRef>
        get() = fetchFilteredData(UtxoComponentGroup.REFERENCES.ordinal)
    override val signatories: UtxoFilteredData<PublicKey>
        get() = fetchFilteredData(UtxoComponentGroup.SIGNATORIES.ordinal)
    override val timeWindow: TimeWindow?
        get() = filteredTransaction
            .getComponentGroupContent(UtxoComponentGroup.NOTARY.ordinal)
            ?.singleOrNull { it.first == WrappedUtxoWireTransaction.timeWindowIndex }
            ?.let { serializationService.deserialize(it.second, TimeWindow::class.java) }

    override fun verify() {
        filteredTransaction.verify()
    }

    private class FilteredDataRemovedImpl<T> : UtxoFilteredData.Removed<T>

    private class FilteredDataSizeImpl<T>(override val size: Int) : UtxoFilteredData.SizeOnly<T>

    private class FilteredDataAuditImpl<T>(
        override val size: Int,
        override val values: Map<Int, T>
    ) : UtxoFilteredData.Audit<T>


    private inline fun <reified T : Any> fetchFilteredData(index: Int): UtxoFilteredData<T> {
        return filteredTransaction.filteredComponentGroups.getOrDefault(index, null)
            ?.let {
                when (it.merkleProofType) {
                    MerkleProofType.SIZE -> return FilteredDataSizeImpl(it.merkleProof.treeSize)
                    MerkleProofType.AUDIT -> return FilteredDataAuditImpl(
                        it.merkleProof.treeSize,
                        it.merkleProof.leaves.associateBy(
                            { leaf -> leaf.index },
                            { leaf -> serializationService.deserialize(leaf.leafData, T::class.java) })
                    )
                }
            }
            ?: return FilteredDataRemovedImpl()
    }

}

