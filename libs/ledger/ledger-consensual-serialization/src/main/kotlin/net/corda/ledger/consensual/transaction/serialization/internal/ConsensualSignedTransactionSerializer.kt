package net.corda.ledger.consensual.transaction.serialization.internal

import net.corda.ledger.consensual.impl.transaction.ConsensualSignedTransactionImpl
import net.corda.ledger.consensual.transaction.serialization.ConsensualSignedTransactionImplVersion
import net.corda.ledger.consensual.transaction.serialization.ConsensualSignedTransactionProxy
import net.corda.serialization.BaseProxySerializer
import net.corda.serialization.InternalCustomSerializer
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

// need to decide where we want to keep our serializers since they seem to be split over different places atm.
@Component(service = [InternalCustomSerializer::class])
class ConsensualSignedTransactionSerializer @Activate constructor(
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService
) : BaseProxySerializer<ConsensualSignedTransaction, ConsensualSignedTransactionProxy>() {

    override val type = ConsensualSignedTransaction::class.java

    override val proxyType = ConsensualSignedTransactionProxy::class.java

    override val withInheritance = true

    override fun toProxy(obj: ConsensualSignedTransaction): ConsensualSignedTransactionProxy {
        return ConsensualSignedTransactionProxy(
            ConsensualSignedTransactionImplVersion.VERSION_1,
            (obj as ConsensualSignedTransactionImpl).wireTransaction,
            obj.signatures
        )
    }

    override fun fromProxy(proxy: ConsensualSignedTransactionProxy): ConsensualSignedTransaction {
        if (proxy.version == ConsensualSignedTransactionImplVersion.VERSION_1) {
            return ConsensualSignedTransactionImpl(
                serializationService,
                proxy.wireTransaction,
                proxy.signatures
            )
        }
        throw CordaRuntimeException("Unable to create ConsensualSignedTransaction with Version='${proxy.version}'")
    }
}