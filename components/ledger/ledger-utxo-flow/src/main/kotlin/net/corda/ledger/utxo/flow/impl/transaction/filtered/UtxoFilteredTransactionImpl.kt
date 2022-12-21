package net.corda.ledger.utxo.flow.impl.transaction.filtered

import net.corda.ledger.common.flow.transaction.filtered.FilteredTransaction
import net.corda.ledger.utxo.data.state.StateAndRefImpl
import net.corda.ledger.utxo.data.state.TransactionStateImpl
import net.corda.ledger.utxo.data.state.getEncumbranceGroup
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.ledger.utxo.data.transaction.UtxoOutputInfoComponent
import net.corda.ledger.utxo.data.transaction.WrappedUtxoWireTransaction
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.application.serialization.deserialize
import net.corda.v5.base.util.loggerFor
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

    private companion object {
        val logger = loggerFor<UtxoFilteredTransactionImpl>()
    }

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
                    logger.error("AUDIT VAGYOK VALAMIERT?")
                    when (val filteredStateInfos =
                        getFilteredData<UtxoOutputInfoComponent>(UtxoComponentGroup.OUTPUTS_INFO.ordinal)) {
                        is UtxoFilteredData.Audit -> {
                            val values = filteredOutputStates.values.entries.associateBy(
                                keySelector = { (key, _) -> key },
                                valueTransform = { (key, value) ->
                                    val info = filteredStateInfos.values[key]
                                        ?: throw FilteredDataInconsistencyException("Missing output info")
                                    StateAndRefImpl(
                                        state = TransactionStateImpl(value, info.notary, info.getEncumbranceGroup()),
                                        ref = StateRef(id, key)
                                    )
                                }
                            )
                            FilteredDataAuditImpl(filteredOutputStates.size, values)
                        }

                        else -> FilteredDataSizeImpl(0)
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
        logger.error("Ez vagyok: $index")
        return filteredTransaction.filteredComponentGroups[index]
            ?.let { group ->
                when (group.merkleProof.proofType) {
                    MerkleProofType.SIZE -> {
                        logger.error("size vagyok: $index")
                        FilteredDataSizeImpl(group.merkleProof.treeSize)
                    }
                    MerkleProofType.AUDIT -> {
                        logger.error("audit vagyok: $index")
                        // if it's an audit proof of an empty list, we need to strip the marker
                        return if (group.merkleProof.leaves.size == 1
                            && group.merkleProof.leaves.first().leafData.size == 0)
                            FilteredDataAuditImpl(
                                0,
                                emptyMap()
                            )
                        else {
                            logger.error("audit vagyok: $index")
                            FilteredDataAuditImpl(
                                group.merkleProof.treeSize,
                                group.merkleProof.leaves.associateBy(
                                    { leaf -> leaf.index },
                                    { leaf ->
                                        serializationService.deserialize(leaf.leafData)
                                    }
                                )
                            )
                        }
                    }
                }
            } ?: return FilteredDataRemovedImpl()
    }

}

