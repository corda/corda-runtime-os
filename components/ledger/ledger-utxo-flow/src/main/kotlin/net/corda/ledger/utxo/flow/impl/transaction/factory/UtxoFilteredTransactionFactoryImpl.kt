package net.corda.ledger.utxo.flow.impl.transaction.factory

import net.corda.ledger.common.flow.transaction.filtered.FilteredTransaction
import net.corda.ledger.utxo.flow.impl.transaction.filtered.UtxoFilteredTransactionImpl
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.transaction.UtxoFilteredTransaction
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope

@Component(
    service = [UtxoFilteredTransactionFactory::class, UsedByFlow::class],
    scope = ServiceScope.PROTOTYPE
)
class UtxoFilteredTransactionFactoryImpl @Activate constructor(
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService
) : UtxoFilteredTransactionFactory, UsedByFlow {
    @Suspendable
    override fun create(filteredTransaction: FilteredTransaction): UtxoFilteredTransaction {
        return UtxoFilteredTransactionImpl(serializationService, filteredTransaction)
    }
}