package net.corda.ledger.utxo.flow.impl.transaction.serializer.amqp

import net.corda.ledger.utxo.data.transaction.UtxoFilteredTransactionAndSignaturesImpl
import net.corda.sandbox.type.SandboxConstants.CORDA_UNINJECTABLE_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.serialization.BaseProxySerializer
import net.corda.serialization.InternalCustomSerializer
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransaction
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

/**
 * [UtxoFilteredTransactionAndSignaturesSerializer] is needed to serialize [UtxoFilteredTransactionAndSignaturesImpl] as the default AMQP
 * serialization does not like that [UTxoFilteredTransactionAndSignatures.signatures] returns a [List] but the underlying property (of the
 * same name) is a [Set].
 */
@Component(
    service = [ InternalCustomSerializer::class, UsedByFlow::class ],
    property = [ CORDA_UNINJECTABLE_SERVICE ],
    scope = PROTOTYPE
)
class UtxoFilteredTransactionAndSignaturesSerializer @Activate constructor(
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService
) : BaseProxySerializer<UtxoFilteredTransactionAndSignaturesImpl, UtxoFilteredTransactionAndSignaturesProxy>(), UsedByFlow {

    override fun toProxy(obj: UtxoFilteredTransactionAndSignaturesImpl): UtxoFilteredTransactionAndSignaturesProxy {
        return UtxoFilteredTransactionAndSignaturesProxy(obj.filteredTransaction, obj.signatures)
    }

    override fun fromProxy(proxy: UtxoFilteredTransactionAndSignaturesProxy): UtxoFilteredTransactionAndSignaturesImpl {
        return UtxoFilteredTransactionAndSignaturesImpl(proxy.filteredTransaction, proxy.signatures.toSet())
    }

    override val proxyType
        get() = UtxoFilteredTransactionAndSignaturesProxy::class.java

    override val type
        get() = UtxoFilteredTransactionAndSignaturesImpl::class.java

    override val withInheritance
        get() = false
}

data class UtxoFilteredTransactionAndSignaturesProxy(
    val filteredTransaction: UtxoFilteredTransaction,
    val signatures: List<DigitalSignatureAndMetadata>
)
