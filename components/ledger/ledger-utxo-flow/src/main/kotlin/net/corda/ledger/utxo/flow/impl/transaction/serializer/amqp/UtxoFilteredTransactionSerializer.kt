package net.corda.ledger.utxo.flow.impl.transaction.serializer.amqp

import net.corda.ledger.common.flow.transaction.filtered.FilteredTransaction
import net.corda.ledger.utxo.flow.impl.transaction.filtered.UtxoFilteredTransactionImpl
import net.corda.sandbox.type.UsedByFlow
import net.corda.serialization.BaseProxySerializer
import net.corda.serialization.InternalCustomSerializer
import net.corda.v5.application.serialization.SerializationService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope

@Component(
    service = [InternalCustomSerializer::class, UsedByFlow::class],
    scope = ServiceScope.PROTOTYPE
)
class UtxoFilteredTransactionSerializer @Activate constructor(
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService
) : BaseProxySerializer<UtxoFilteredTransactionImpl, UtxoFilteredTransactionProxy>(), UsedByFlow {

    override fun toProxy(obj: UtxoFilteredTransactionImpl): UtxoFilteredTransactionProxy {
        return UtxoFilteredTransactionProxy(obj.filteredTransaction)
    }

    override fun fromProxy(proxy: UtxoFilteredTransactionProxy): UtxoFilteredTransactionImpl {
        return UtxoFilteredTransactionImpl(serializationService, proxy.filteredTransaction)
    }

    override val proxyType = UtxoFilteredTransactionProxy::class.java

    override val type = UtxoFilteredTransactionImpl::class.java

    override val withInheritance = false
}


data class UtxoFilteredTransactionProxy(
    val filteredTransaction: FilteredTransaction
)