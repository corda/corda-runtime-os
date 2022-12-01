package net.corda.ledger.utxo.flow.impl.transaction.filtered

import net.corda.ledger.common.flow.transaction.filtered.FilteredTransaction
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.merkle.MerkleProofType
import net.corda.v5.ledger.common.Party
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
    private val filteredTransaction: FilteredTransaction) : UtxoFilteredTransaction {
    override val commands: UtxoFilteredData<Command>
        get() = fetchFilteredData<Command>(UtxoComponentGroup.COMMANDS.ordinal)
    override val id: SecureHash
        get() = filteredTransaction.id
    override val inputStateRefs: UtxoFilteredData<StateRef>
        get() = fetchFilteredData(UtxoComponentGroup.INPUTS.ordinal)
    override val notary: Party?
        get() = filteredTransaction
            .getComponentGroupContent(UtxoComponentGroup.NOTARY.ordinal)
            ?.singleOrNull { it.first == 0 }
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
            ?.singleOrNull { it.first == 1 }
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

    private inline fun <reified T: Any> fetchFilteredData(index: Int) : UtxoFilteredData<T> {
        val merkleProof = filteredTransaction.filteredComponentGroups.getOrDefault(index, null)
            ?: return FilteredDataRemovedImpl<T>()

        if (merkleProof.merkleProofType == MerkleProofType.SIZE) {
            return FilteredDataSizeImpl(merkleProof.merkleProof.treeSize)
        }

        return FilteredDataAuditImpl<T>(
            merkleProof.merkleProof.treeSize,
            merkleProof.merkleProof.leaves.map { leaf ->
                FilteredEntryImpl<T>(
                    leaf.index,
                    serializationService.deserialize(leaf.leafData, T::class.java)
                )
            }
        )
    }

}

