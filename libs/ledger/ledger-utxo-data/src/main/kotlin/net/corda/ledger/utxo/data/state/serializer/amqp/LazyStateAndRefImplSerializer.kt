package net.corda.ledger.utxo.data.state.serializer.amqp

import net.corda.ledger.utxo.data.state.LazyStateAndRefImpl
import net.corda.ledger.utxo.data.transaction.UtxoTransactionOutputDto
import net.corda.serialization.BaseProxySerializer
import net.corda.serialization.InternalCustomSerializer
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.ledger.utxo.ContractState
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [ InternalCustomSerializer::class ])
class LazyStateAndRefSerializer @Activate constructor(
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService
): BaseProxySerializer<LazyStateAndRefImpl<ContractState>, UtxoTransactionOutputDto>() {
    override val type
        get() = LazyStateAndRefImpl::class.java

    override val proxyType
        get() = UtxoTransactionOutputDto::class.java

    override val withInheritance
        // LazyStateAndRefImpl is a final class.
        get() = false

    override fun toProxy(obj: LazyStateAndRefImpl<ContractState>): UtxoTransactionOutputDto {
        return obj.serializedStateAndRef
    }

    override fun fromProxy(proxy: UtxoTransactionOutputDto): LazyStateAndRefImpl<ContractState> {
        return proxy.toStateAndRef(serializationService)
    }
}