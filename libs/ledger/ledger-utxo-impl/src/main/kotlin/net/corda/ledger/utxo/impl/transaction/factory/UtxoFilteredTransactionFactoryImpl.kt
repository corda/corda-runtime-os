package net.corda.ledger.utxo.impl.transaction.factory

import java.util.function.Predicate
import net.corda.ledger.common.impl.transaction.WireTransactionImpl
import net.corda.ledger.common.transaction.ComponentGroupFilterParameters
import net.corda.ledger.common.transaction.FilteredTransaction
import net.corda.ledger.common.transaction.MerkleProofType.AUDIT
import net.corda.ledger.common.transaction.MerkleProofType.SIZE
import net.corda.ledger.common.transaction.TransactionMetadata
import net.corda.ledger.common.transaction.factory.FilteredTransactionFactory
import net.corda.ledger.utxo.transaction.factory.UtxoFilteredTransactionFactory
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.Attachment
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.TimeWindow
import net.corda.v5.ledger.utxo.UtxoComponentGroup.ATTACHMENTS
import net.corda.v5.ledger.utxo.UtxoComponentGroup.COMMANDS
import net.corda.v5.ledger.utxo.UtxoComponentGroup.INPUTS
import net.corda.v5.ledger.utxo.UtxoComponentGroup.OUTPUTS
import net.corda.v5.ledger.utxo.UtxoComponentGroup.TIME_WINDOW
import net.corda.v5.ledger.utxo.UtxoComponentGroup.TRANSACTION_PARAMETERS
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [UtxoFilteredTransactionFactory::class])
class UtxoFilteredTransactionFactoryImpl @Activate constructor(
    @Reference(service = FilteredTransactionFactory::class)
    private val filteredTransactionFactory: FilteredTransactionFactory
) : UtxoFilteredTransactionFactory {

    @Suspendable
    override fun create(wireTransaction: WireTransactionImpl, filter: Predicate<Any>): FilteredTransaction {
        // TODO Add all [UtxoComponentGroup]s
        return filteredTransactionFactory.create(
            wireTransaction,
            componentGroupFilterParameters = listOf(
                ComponentGroupFilterParameters(TRANSACTION_PARAMETERS.ordinal, TransactionMetadata::class.java, AUDIT),
                ComponentGroupFilterParameters(INPUTS.ordinal, StateRef::class.java, AUDIT),
                ComponentGroupFilterParameters(OUTPUTS.ordinal, ContractState::class.java, SIZE),
                ComponentGroupFilterParameters(ATTACHMENTS.ordinal, Attachment::class.java, AUDIT),
                ComponentGroupFilterParameters(TIME_WINDOW.ordinal, TimeWindow::class.java, AUDIT),
                ComponentGroupFilterParameters(COMMANDS.ordinal, Command::class.java, AUDIT)
            ),
            filter
        )
    }
}