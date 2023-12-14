package net.corda.ledger.utxo.flow.impl.transaction.serializer.amqp

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.flow.transaction.TransactionSignatureServiceInternal
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionImpl
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoLedgerTransactionFactory
import net.corda.ledger.utxo.flow.impl.transaction.verifier.NotarySignatureVerificationServiceInternal
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
class UtxoSignedTransactionSerializer @Activate constructor(
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService,
    @Reference(service = TransactionSignatureServiceInternal::class)
    private val transactionSignatureService: TransactionSignatureServiceInternal,
    @Reference(service = UtxoLedgerTransactionFactory::class)
    private val utxoLedgerTransactionFactory: UtxoLedgerTransactionFactory,
    @Reference(service = NotarySignatureVerificationServiceInternal::class)
    private val notarySignatureVerificationService: NotarySignatureVerificationServiceInternal
) : BaseProxySerializer<UtxoSignedTransactionInternal, UtxoSignedTransactionProxy>(), UsedByFlow {
    private companion object {
        private const val VERSION_1 = 1
    }

    override val type
        get() = UtxoSignedTransactionInternal::class.java

    override val proxyType
        get() = UtxoSignedTransactionProxy::class.java

    override val withInheritance
        // UtxoSignedTransactionInternal is an interface.
        get() = true

    override fun toProxy(obj: UtxoSignedTransactionInternal): UtxoSignedTransactionProxy {
        return UtxoSignedTransactionProxy(
            VERSION_1,
            obj.wireTransaction,
            obj.signatures
        )
    }

    override fun fromProxy(proxy: UtxoSignedTransactionProxy): UtxoSignedTransactionInternal {
        return when (proxy.version) {
            VERSION_1 ->
                UtxoSignedTransactionImpl(
                    serializationService,
                    transactionSignatureService,
                    notarySignatureVerificationService,
                    utxoLedgerTransactionFactory,
                    proxy.wireTransaction,
                    proxy.signatures
                )
            else ->
                throw CordaRuntimeException("Unable to create UtxoSignedTransaction with Version='${proxy.version}'")
        }
    }
}

/**
 * The class that actually gets serialized on the wire.
 */
data class UtxoSignedTransactionProxy(
    /**
     * Version of container.
     */
    val version: Int,

    /**
     * Properties for Utxo Signed transactions' serialisation.
     */
    val wireTransaction: WireTransaction,
    val signatures: List<DigitalSignatureAndMetadata>
)
