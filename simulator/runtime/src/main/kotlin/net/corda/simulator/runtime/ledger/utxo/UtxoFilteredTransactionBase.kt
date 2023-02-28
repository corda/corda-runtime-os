package net.corda.simulator.runtime.ledger.utxo

import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TimeWindow
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredData
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransaction
import net.corda.v5.ledger.utxo.transaction.getOutputStates
import java.security.PublicKey
import java.util.function.Predicate

class UtxoFilteredTransactionBase(
    private val signedTransaction: UtxoSignedTransaction,
    private val builder: UtxoFilteredTransactionBuilderBase,
    private val filterParams: Map<UtxoComponentGroup, FilterParams?>
) : UtxoFilteredTransaction {

    override val id: SecureHash
        get() = signedTransaction.id

    override val notary: Party?
        get()  {
            if(builder.notary){
                return signedTransaction.notary
            }
            return null
        }

    override val timeWindow: TimeWindow?
        get() {
            if(builder.timeWindow){
                return signedTransaction.timeWindow
            }
            return null
        }

    override val commands: UtxoFilteredData<Command>
        get() = getFilteredData(UtxoComponentGroup.COMMANDS, signedTransaction.commands)
    override val inputStateRefs: UtxoFilteredData<StateRef>
        get() = getFilteredData(UtxoComponentGroup.INPUTS, signedTransaction.inputStateRefs)
    override val metadata: TransactionMetadata
        get() = signedTransaction.metadata
    override val outputStateAndRefs: UtxoFilteredData<StateAndRef<*>>
        get() = getFilteredData(UtxoComponentGroup.OUTPUTS, signedTransaction.toLedgerTransaction().getOutputStates())
    override val referenceStateRefs: UtxoFilteredData<StateRef>
        get() = getFilteredData(UtxoComponentGroup.REFERENCES, signedTransaction.referenceStateRefs)
    override val signatories: UtxoFilteredData<PublicKey>
        get() = getFilteredData(UtxoComponentGroup.SIGNATORIES, signedTransaction.signatories)
    override fun verify() {

    }

    @Suppress( "UNCHECKED_CAST")
    private inline fun <reified T : Any> getFilteredData(
        utxoComponent: UtxoComponentGroup,
        component: List<T>
    ): UtxoFilteredData<T> {
        val filterParam = filterParams[utxoComponent] ?: return  FilteredDataRemovedImpl()
        if(filterParam.filterType == FilterType.SIZE){
            return FilteredDataSizeImpl(component.size)
        }else{

            val filteredComponents = component.mapIndexed { index, comp -> index to comp }
                .filter {
                    requireNotNull(filterParam.predicate)
                    (filterParam.predicate as Predicate<Any>).test(it.second)
                }

            if (filteredComponents.isEmpty()) {
                if (component.isEmpty()) {
                    return FilteredDataAuditImpl(0, emptyMap())
                } else {
                    return FilteredDataSizeImpl(0)
                }
            } else {
                return FilteredDataAuditImpl(
                    component.size,
                    filteredComponents.associateBy(
                        { pair -> pair.first },
                        { pair -> pair.second }
                    )
                )
            }
        }
    }
}

private class FilteredDataSizeImpl<T>(override val size: Int) : UtxoFilteredData.SizeOnly<T>

private class FilteredDataRemovedImpl<T> : UtxoFilteredData.Removed<T>

private class FilteredDataAuditImpl<T>(
    override val size: Int,
    override val values: Map<Int, T>
) : UtxoFilteredData.Audit<T>