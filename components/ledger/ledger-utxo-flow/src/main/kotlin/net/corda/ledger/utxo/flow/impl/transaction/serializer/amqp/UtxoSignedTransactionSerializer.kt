package net.corda.ledger.utxo.flow.impl.transaction.serializer.amqp

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.flow.transaction.TransactionSignatureService
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionImpl
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.sandbox.type.SandboxConstants.CORDA_UNINJECTABLE_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandbox.type.UsedByVerification
import net.corda.serialization.BaseProxySerializer
import net.corda.serialization.InternalCustomSerializer
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(
    service = [ InternalCustomSerializer::class, UsedByFlow::class, UsedByVerification::class ],
    property = [ CORDA_UNINJECTABLE_SERVICE ],
    scope = PROTOTYPE
)
class UtxoSignedTransactionSerializer @Activate constructor(
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService,
    @Reference(service = TransactionSignatureService::class)
    private val transactionSignatureService: TransactionSignatureService
) : BaseProxySerializer<UtxoSignedTransactionInternal, UtxoSignedTransactionProxy>(),
    UsedByFlow, UsedByVerification {

    override val type = UtxoSignedTransactionInternal::class.java

    override val proxyType = UtxoSignedTransactionProxy::class.java

    override val withInheritance = true

    override fun toProxy(obj: UtxoSignedTransactionInternal): UtxoSignedTransactionProxy {
        return UtxoSignedTransactionProxy(
            UtxoSignedTransactionVersion.VERSION_1,
            obj.wireTransaction,
            obj.signatures
        )
    }

    override fun fromProxy(proxy: UtxoSignedTransactionProxy): UtxoSignedTransactionInternal {
        if (proxy.version == UtxoSignedTransactionVersion.VERSION_1) {
            return UtxoSignedTransactionImpl(
                serializationService,
                transactionSignatureService,
                proxy.wireTransaction,
                proxy.signatures
            )
        }
        throw CordaRuntimeException("Unable to create UtxoSignedTransaction with Version='${proxy.version}'")
    }
}

/**
 * The class that actually gets serialized on the wire.
 */
data class UtxoSignedTransactionProxy(
    /**
     * Version of container.
     */
    val version: UtxoSignedTransactionVersion,

    /**
     * Properties for Utxo Signed transactions' serialisation.
     */
    val wireTransaction: WireTransaction,
    val signatures: List<DigitalSignatureAndMetadata>
)

/**
 * Enumeration for UtxoSignedTransaction version.
 */
@CordaSerializable
enum class UtxoSignedTransactionVersion {
    VERSION_1
}