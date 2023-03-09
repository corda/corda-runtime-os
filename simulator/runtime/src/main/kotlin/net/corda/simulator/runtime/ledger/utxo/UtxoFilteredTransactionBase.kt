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
import java.security.PublicKey
import java.util.function.Predicate

class UtxoFilteredTransactionBase(
    private val signedTransaction: UtxoSignedTransaction,
    private val builder: UtxoFilteredTransactionBuilderBase,
    private val filterParams: Map<UtxoComponentGroup, FilterParams?>
) : UtxoFilteredTransaction {

    override fun getId(): SecureHash {
        return signedTransaction.id
    }

    override fun getMetadata(): TransactionMetadata {
        return signedTransaction.metadata
    }

    override fun getTimeWindow(): TimeWindow? {
        if(builder.timeWindow){
            return signedTransaction.timeWindow
        }
        return null
    }

    override fun getNotary(): Party? {
        if(builder.notary){
            return signedTransaction.notary
        }
        return null
    }

    override fun getSignatories(): UtxoFilteredData<PublicKey> {
        return getFilteredData(UtxoComponentGroup.SIGNATORIES, signedTransaction.signatories)
    }

    override fun getCommands(): UtxoFilteredData<Command> {
        return getFilteredData(UtxoComponentGroup.COMMANDS, signedTransaction.commands)
    }

    override fun getInputStateRefs(): UtxoFilteredData<StateRef> {
        return getFilteredData(UtxoComponentGroup.INPUTS, signedTransaction.inputStateRefs)
    }

    override fun getReferenceStateRefs(): UtxoFilteredData<StateRef> {
        return getFilteredData(UtxoComponentGroup.REFERENCES, signedTransaction.referenceStateRefs)
    }

    override fun getOutputStateAndRefs(): UtxoFilteredData<StateAndRef<*>> {
        return getFilteredData(UtxoComponentGroup.OUTPUTS, signedTransaction.toLedgerTransaction().outputStateAndRefs)
    }

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

private class FilteredDataSizeImpl<T>(private val size: Int) : UtxoFilteredData.SizeOnly<T> {
    override fun getSize(): Int {
        return size
    }
}

private class FilteredDataRemovedImpl<T> : UtxoFilteredData.Removed<T>

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