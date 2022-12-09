package net.corda.ledger.utxo.flow.impl.transaction.filtered

import net.corda.ledger.common.flow.transaction.filtered.FilteredTransaction
import net.corda.ledger.utxo.data.state.StateAndRefImpl
import net.corda.ledger.utxo.data.state.TransactionStateImpl
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.ledger.utxo.data.transaction.UtxoOutputInfoComponent
import net.corda.ledger.utxo.data.transaction.WrappedUtxoWireTransaction
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.application.serialization.deserialize
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

class UtxoFilteredTransactionImpl(
    private val serializationService: SerializationService,
    val filteredTransaction: FilteredTransaction
) : UtxoFilteredTransaction {

    override val id: SecureHash
        get() = filteredTransaction.id

    override val commands: UtxoFilteredData<Command>
        get() = getFilteredData(UtxoComponentGroup.COMMANDS.ordinal)

    override val inputStateRefs: UtxoFilteredData<StateRef>
        get() = getFilteredData(UtxoComponentGroup.INPUTS.ordinal)

    override val metadata: TransactionMetadata
        get() = filteredTransaction.metadata

    override val notary: Party?
        get() = filteredTransaction
            .getComponentGroupContent(UtxoComponentGroup.NOTARY.ordinal)
            ?.singleOrNull { it.first == WrappedUtxoWireTransaction.notaryIndex }
            ?.let { serializationService.deserialize(it.second, Party::class.java) }

    override val outputStateAndRefs: UtxoFilteredData<StateAndRef<*>>
        get() = getFilteredData<ContractState>(UtxoComponentGroup.OUTPUTS.ordinal).let { filteredOutputStates ->
            when (filteredOutputStates) {
                is UtxoFilteredData.Removed<ContractState> -> FilteredDataRemovedImpl()
                is UtxoFilteredData.SizeOnly -> FilteredDataSizeImpl(filteredOutputStates.size)
                is UtxoFilteredData.Audit -> {
                    when (val filteredStateInfos = getFilteredData<UtxoOutputInfoComponent>(UtxoComponentGroup.OUTPUTS_INFO.ordinal)) {
                        is UtxoFilteredData.Audit -> {
                            val values = filteredOutputStates.values.entries.associateBy(
                                keySelector = { (key, _) -> key },
                                valueTransform = { (key, value) ->
                                    val info = filteredStateInfos.values[key]
                                        ?: throw FilteredDataInconsistencyException("Missing output info")
                                    StateAndRefImpl(
                                        state = TransactionStateImpl(value, info.notary, info.encumbrance),
                                        ref = StateRef(id, key)
                                    )
                                }
                            )
                            FilteredDataAuditImpl(filteredOutputStates.size, values)
                        }
                        else -> throw FilteredDataInconsistencyException("Output infos have been removed. Cannot reconstruct outputs")
                    }
                }

                else -> throw FilteredDataInconsistencyException("Unknown filtered data type.")
            }
        }

    override val referenceInputStateRefs: UtxoFilteredData<StateRef>
        get() = getFilteredData(UtxoComponentGroup.REFERENCES.ordinal)

    override val signatories: UtxoFilteredData<PublicKey>
        get() = getFilteredData(UtxoComponentGroup.SIGNATORIES.ordinal)

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


    private inline fun <reified T : Any> getFilteredData(index: Int): UtxoFilteredData<T> {
        return filteredTransaction.filteredComponentGroups[index]
            ?.let { group ->
                when (group.merkleProof.proofType) {
                    MerkleProofType.SIZE -> return FilteredDataSizeImpl(group.merkleProof.treeSize)
                    MerkleProofType.AUDIT -> return FilteredDataAuditImpl(
                        group.merkleProof.treeSize,
                        group.merkleProof.leaves.associateBy(
                            { leaf -> leaf.index },
                            { leaf -> serializationService.deserialize(leaf.leafData) }
                        )
                    )
                }
            } ?: return FilteredDataRemovedImpl()
    }

}

