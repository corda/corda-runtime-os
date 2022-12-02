package net.corda.ledger.utxo.flow.impl.transaction.filtered

import net.corda.ledger.common.flow.transaction.filtered.FilteredTransaction
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.ledger.utxo.data.transaction.WrappedUtxoWireTransaction
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.merkle.MerkleProofType
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TimeWindow
import net.corda.v5.ledger.utxo.transaction.FilteredEntry
import net.corda.v5.ledger.utxo.transaction.UtxoFilteredData
import net.corda.v5.ledger.utxo.transaction.UtxoFilteredTransaction
import java.security.PublicKey

class UtxoFilteredTransactionImpl(
    private val serializationService: SerializationService,
    val filteredTransaction: FilteredTransaction) : UtxoFilteredTransaction {
    override val commands: UtxoFilteredData<Command>
        get() = fetchFilteredData<Command>(UtxoComponentGroup.COMMANDS.ordinal)
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
        get() = fetchFilteredData(UtxoComponentGroup.OUTPUTS.ordinal)
    override val referenceInputStateRefs: UtxoFilteredData<StateRef>
        get() = fetchFilteredData(UtxoComponentGroup.REFERENCES.ordinal)
    override val signatories: UtxoFilteredData<PublicKey>
        get() = TODO("Not yet implemented")
    override val timeWindow: TimeWindow?
        get() = filteredTransaction
            .getComponentGroupContent(UtxoComponentGroup.NOTARY.ordinal)
            ?.singleOrNull { it.first == WrappedUtxoWireTransaction.timeWindowIndex }
            ?.let { serializationService.deserialize(it.second, TimeWindow::class.java) }

    override fun verify() {
        filteredTransaction.verify()
    }

    private class FilteredDataRemovedImpl<T>: UtxoFilteredData.UtxoFilteredDataRemoved<T>

    private class FilteredDataSizeImpl<T>( override val size: Int ): UtxoFilteredData.UtxoFilteredDataSizeOnly<T>

    private class FilteredDataAuditImpl<T>(
        override val size: Int,
        override val values: List<FilteredEntry<T>>) : UtxoFilteredData.UtxoFilteredDataAudit<T>

    private class FilteredEntryImpl<T>(override val index: Int, override val value: T) : FilteredEntry<T>

    private inline fun <reified T : Any> fetchFilteredData(index: Int): UtxoFilteredData<T> {
        return filteredTransaction.filteredComponentGroups.getOrDefault(index, null)
            ?.let {
                when (it.merkleProofType) {
                    MerkleProofType.SIZE -> return FilteredDataSizeImpl(it.merkleProof.treeSize)
                    MerkleProofType.AUDIT -> return FilteredDataAuditImpl(
                        it.merkleProof.treeSize,
                        it.merkleProof.leaves.map { leaf ->
                            FilteredEntryImpl(
                                leaf.index,
                                serializationService.deserialize(leaf.leafData, T::class.java)
                            )
                        }
                    )

                }
            }
            ?: return FilteredDataRemovedImpl<T>()
    }

}

