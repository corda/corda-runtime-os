package net.corda.ledger.utxo.flow.impl.transaction.serializer.amqp

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionImpl
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandbox.type.UsedByVerification
import net.corda.serialization.BaseProxySerializer
import net.corda.serialization.InternalCustomSerializer
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope

@Component(
    service = [ InternalCustomSerializer::class, UsedByFlow::class, UsedByVerification::class ],
    scope = ServiceScope.PROTOTYPE
)class UtxoSignedTransactionSerializer @Activate constructor(
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService,
    @Reference(service = SigningService::class)
    private val signingService: SigningService,
    @Reference(service = DigitalSignatureVerificationService::class)
    private val digitalSignatureVerificationService: DigitalSignatureVerificationService
) : BaseProxySerializer<UtxoSignedTransaction, UtxoSignedTransactionProxy>(),
    UsedByFlow, UsedByVerification {

    override val type = UtxoSignedTransaction::class.java

    override val proxyType = UtxoSignedTransactionProxy::class.java

    override val withInheritance = true

    override fun toProxy(obj: UtxoSignedTransaction): UtxoSignedTransactionProxy {
        return UtxoSignedTransactionProxy(
            UtxoSignedTransactionVersion.VERSION_1,
            (obj as UtxoSignedTransactionImpl).wireTransaction,
            obj.signatures
        )
    }

    override fun fromProxy(proxy: UtxoSignedTransactionProxy): UtxoSignedTransaction {
        if (proxy.version == UtxoSignedTransactionVersion.VERSION_1) {
            return UtxoSignedTransactionImpl(
                serializationService,
                signingService,
                digitalSignatureVerificationService,
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