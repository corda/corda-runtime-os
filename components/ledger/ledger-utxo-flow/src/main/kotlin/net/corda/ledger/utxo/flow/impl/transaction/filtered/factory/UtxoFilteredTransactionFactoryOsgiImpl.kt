package net.corda.ledger.utxo.flow.impl.transaction.filtered.factory

import net.corda.ledger.common.data.transaction.filtered.factory.FilteredTransactionFactory
import net.corda.ledger.lib.utxo.flow.impl.transaction.filtered.factory.UtxoFilteredTransactionFactory
import net.corda.ledger.lib.utxo.flow.impl.transaction.filtered.factory.UtxoFilteredTransactionFactoryImpl
import net.corda.sandbox.type.SandboxConstants
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.serialization.SerializationService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope

@Component(
    service = [UtxoFilteredTransactionFactory::class, UsedByFlow::class],
    property = [SandboxConstants.CORDA_UNINJECTABLE_SERVICE],
    scope = ServiceScope.PROTOTYPE
)
class UtxoFilteredTransactionFactoryOsgiImpl(
    delegate: UtxoFilteredTransactionFactory
) : UtxoFilteredTransactionFactory by delegate, UsedByFlow {
    @Activate
    constructor(
        @Reference(service = FilteredTransactionFactory::class)
        filteredTransactionFactory: FilteredTransactionFactory,
        @Reference(service = SerializationService::class)
        serializationService: SerializationService
    ) : this(
        UtxoFilteredTransactionFactoryImpl(filteredTransactionFactory, serializationService)
    )
}
