package net.corda.ledger.consensual.transaction.serialization.internal

import net.corda.ledger.consensual.impl.transaction.ConsensualSignedTransactionImpl
import net.corda.ledger.consensual.transaction.serialization.ConsensualSignedTransactionImplContainer
import net.corda.ledger.consensual.transaction.serialization.ConsensualSignedTransactionImplVersion
import net.corda.serialization.BaseProxySerializer
import net.corda.serialization.InternalCustomSerializer
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [InternalCustomSerializer::class])
class ConsensualSignedTransactionImplSerializer @Activate constructor(
    @Reference(service = SerializationService::class) private val serializationService: SerializationService
) : BaseProxySerializer<ConsensualSignedTransactionImpl, ConsensualSignedTransactionImplContainer>() {

    override fun toProxy(obj: ConsensualSignedTransactionImpl): ConsensualSignedTransactionImplContainer =
        ConsensualSignedTransactionImplContainer(
            ConsensualSignedTransactionImplVersion.VERSION_1,
            obj.wireTransaction,
            obj.signatures
        )

    override fun fromProxy(proxy: ConsensualSignedTransactionImplContainer): ConsensualSignedTransactionImpl {
        if (proxy.version == ConsensualSignedTransactionImplVersion.VERSION_1) {
            return ConsensualSignedTransactionImpl(
                serializationService,
                proxy.wireTransaction,
                proxy.signatures
            )
        }
        throw CordaRuntimeException("Unable to create ConsensualSignedTransactionImpl with Version='${proxy.version}'")
    }

    override val proxyType: Class<ConsensualSignedTransactionImplContainer>
        get() = ConsensualSignedTransactionImplContainer::class.java
    override val type: Class<ConsensualSignedTransactionImpl>
        get() = ConsensualSignedTransactionImpl::class.java
    override val withInheritance: Boolean
        get() = true
}