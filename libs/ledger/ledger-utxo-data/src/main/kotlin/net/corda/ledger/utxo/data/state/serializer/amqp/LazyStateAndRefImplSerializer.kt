package net.corda.ledger.utxo.data.state.serializer.amqp

import net.corda.ledger.utxo.data.state.LazyStateAndRefImpl
import net.corda.ledger.utxo.data.transaction.UtxoVisibleTransactionOutputDto
import net.corda.sandbox.type.SandboxConstants.CORDA_UNINJECTABLE_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandbox.type.UsedByPersistence
import net.corda.sandbox.type.UsedByVerification
import net.corda.serialization.BaseProxySerializer
import net.corda.serialization.InternalCustomSerializer
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.ContractState
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(
    service = [InternalCustomSerializer::class, UsedByFlow::class, UsedByPersistence::class, UsedByVerification::class],
    property = [CORDA_UNINJECTABLE_SERVICE],
    scope = PROTOTYPE
)
class LazyStateAndRefSerializer @Activate constructor(
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService
) : BaseProxySerializer<LazyStateAndRefImpl<ContractState>, LazyStateAndRefImplProxy>(), UsedByFlow, UsedByPersistence, UsedByVerification {
    private companion object {
        private const val VERSION_1 = 1
    }

    override val type
        get() = LazyStateAndRefImpl::class.java

    override val proxyType
        get() = LazyStateAndRefImplProxy::class.java

    override val withInheritance
        // LazyStateAndRefImpl is a final class.
        get() = false

    override fun toProxy(obj: LazyStateAndRefImpl<ContractState>): LazyStateAndRefImplProxy {
        return LazyStateAndRefImplProxy(
            VERSION_1,
            obj.serializedStateAndRef
        )
    }

    override fun fromProxy(proxy: LazyStateAndRefImplProxy): LazyStateAndRefImpl<ContractState> {
        return when (proxy.version) {
            VERSION_1 ->
                proxy.serializedStateAndRef.toStateAndRef(serializationService)
            else ->
                throw CordaRuntimeException("Unable to create LazyStateAndRefImpl with Version='${proxy.version}'")
        }
    }
}

/**
 * The class that actually gets serialized on the wire.
 */
data class LazyStateAndRefImplProxy(
    /**
     * Version of container.
     */
    val version: Int,

    /**
     * Properties for [LazyStateAndRefImplProxy] serialisation.
     */
    val serializedStateAndRef: UtxoVisibleTransactionOutputDto
)
