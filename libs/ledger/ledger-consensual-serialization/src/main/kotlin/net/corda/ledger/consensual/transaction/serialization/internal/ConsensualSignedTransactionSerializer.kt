package net.corda.ledger.consensual.transaction.serialization.internal

import net.corda.ledger.consensual.impl.transaction.ConsensualSignedTransactionImpl
import net.corda.ledger.consensual.transaction.serialization.ConsensualSignedTransactionImplVersion
import net.corda.ledger.consensual.transaction.serialization.ConsensualSignedTransactionProxy
import net.corda.serialization.BaseProxySerializer
import net.corda.serialization.InternalCustomSerializer
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [InternalCustomSerializer::class])
class ConsensualSignedTransactionSerializer @Activate constructor(
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService,
    @Reference(service = SigningService::class)
    private val signingService: SigningService
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
                signingService,
                proxy.wireTransaction,
                proxy.signatures
            )
        }
        throw CordaRuntimeException("Unable to create ConsensualSignedTransaction with Version='${proxy.version}'")
    }
}