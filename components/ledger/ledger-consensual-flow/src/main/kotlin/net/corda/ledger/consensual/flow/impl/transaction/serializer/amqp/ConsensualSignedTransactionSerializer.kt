package net.corda.ledger.consensual.flow.impl.transaction.serializer.amqp

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.flow.transaction.TransactionSignatureServiceInternal
import net.corda.ledger.consensual.flow.impl.transaction.ConsensualSignedTransactionImpl
import net.corda.ledger.consensual.flow.impl.transaction.ConsensualSignedTransactionInternal
import net.corda.sandbox.type.SandboxConstants.CORDA_UNINJECTABLE_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.serialization.BaseProxySerializer
import net.corda.serialization.InternalCustomSerializer
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(
    service = [ InternalCustomSerializer::class, UsedByFlow::class ],
    property = [ CORDA_UNINJECTABLE_SERVICE ],
    scope = PROTOTYPE
)
class ConsensualSignedTransactionSerializer @Activate constructor(
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService,
    @Reference(service = TransactionSignatureServiceInternal::class)
    private val transactionSignatureService: TransactionSignatureServiceInternal
) : BaseProxySerializer<ConsensualSignedTransactionInternal, ConsensualSignedTransactionProxy>(), UsedByFlow {
    private companion object {
        private const val VERSION_1 = 1
    }

    override val type
        get() = ConsensualSignedTransactionInternal::class.java

    override val proxyType
        get() = ConsensualSignedTransactionProxy::class.java

    override val withInheritance
        // ConsensualSignedTransactionInternal is an interface.
        get() = true

    override fun toProxy(obj: ConsensualSignedTransactionInternal): ConsensualSignedTransactionProxy {
        return ConsensualSignedTransactionProxy(
            VERSION_1,
            obj.wireTransaction,
            obj.signatures
        )
    }

    override fun fromProxy(proxy: ConsensualSignedTransactionProxy): ConsensualSignedTransactionInternal {
        return when (proxy.version) {
            VERSION_1 ->
                ConsensualSignedTransactionImpl(
                    serializationService,
                    transactionSignatureService,
                    proxy.wireTransaction,
                    proxy.signatures
                )
            else ->
                throw CordaRuntimeException("Unable to create ConsensualSignedTransaction with Version='${proxy.version}'")
        }
    }
}

/**
 * The class that actually gets serialized on the wire.
 */
data class ConsensualSignedTransactionProxy(
    /**
     * Version of container.
     */
    val version: Int,

    /**
     * Properties for Consensual Signed transactions' serialisation.
     */
    val wireTransaction: WireTransaction,
    val signatures: List<DigitalSignatureAndMetadata>
)
