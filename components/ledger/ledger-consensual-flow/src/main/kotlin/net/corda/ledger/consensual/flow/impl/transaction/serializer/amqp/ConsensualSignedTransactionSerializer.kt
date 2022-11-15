package net.corda.ledger.consensual.flow.impl.transaction.serializer.amqp

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.flow.transaction.TransactionSignatureService
import net.corda.ledger.consensual.flow.impl.transaction.ConsensualSignedTransactionImpl
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandbox.type.UsedByVerification
import net.corda.serialization.BaseProxySerializer
import net.corda.serialization.InternalCustomSerializer
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(
    service = [InternalCustomSerializer::class, UsedByFlow::class, UsedByVerification::class],
    scope = PROTOTYPE
)
class ConsensualSignedTransactionSerializer @Activate constructor(
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService,
    @Reference(service = TransactionSignatureService::class)
    private val transactionSignatureService: TransactionSignatureService
) : BaseProxySerializer<ConsensualSignedTransaction, ConsensualSignedTransactionProxy>(),
    UsedByFlow, UsedByVerification {

    override val type = ConsensualSignedTransaction::class.java

    override val proxyType = ConsensualSignedTransactionProxy::class.java

    override val withInheritance = true

    override fun toProxy(obj: ConsensualSignedTransaction): ConsensualSignedTransactionProxy {
        return ConsensualSignedTransactionProxy(
            ConsensualSignedTransactionVersion.VERSION_1,
            (obj as ConsensualSignedTransactionImpl).wireTransaction,
            obj.signatures
        )
    }

    override fun fromProxy(proxy: ConsensualSignedTransactionProxy): ConsensualSignedTransaction {
        if (proxy.version == ConsensualSignedTransactionVersion.VERSION_1) {
            return ConsensualSignedTransactionImpl(
                serializationService,
                transactionSignatureService,
                proxy.wireTransaction,
                proxy.signatures
            )
        }
        throw CordaRuntimeException("Unable to create ConsensualSignedTransaction with Version='${proxy.version}'")
    }
}

/**
 * The class that actually gets serialized on the wire.
 */
data class ConsensualSignedTransactionProxy(
    /**
     * Version of container.
     */
    val version: ConsensualSignedTransactionVersion,

    /**
     * Properties for Consensual Signed transactions' serialisation.
     */
    val wireTransaction: WireTransaction,
    val signatures: List<DigitalSignatureAndMetadata>
)

/**
 * Enumeration for ConsensualSignedTransaction version.
 */
@CordaSerializable
enum class ConsensualSignedTransactionVersion {
    VERSION_1
}