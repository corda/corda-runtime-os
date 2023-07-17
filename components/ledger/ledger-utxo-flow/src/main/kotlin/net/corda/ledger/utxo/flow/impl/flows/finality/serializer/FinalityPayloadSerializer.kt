package net.corda.ledger.utxo.flow.impl.flows.finality.serializer

import net.corda.ledger.utxo.flow.impl.flows.finality.FinalityPayload
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.sandbox.type.SandboxConstants.CORDA_UNINJECTABLE_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandbox.type.UsedByPersistence
import net.corda.sandbox.type.UsedByVerification
import net.corda.serialization.BaseProxySerializer
import net.corda.serialization.InternalCustomSerializer
import net.corda.v5.application.serialization.SerializationService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(
    service = [InternalCustomSerializer::class, UsedByFlow::class, UsedByPersistence::class, UsedByVerification::class],
    property = [CORDA_UNINJECTABLE_SERVICE],
    scope = PROTOTYPE
)
class FinalityPayloadSerializer @Activate constructor(
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService
): BaseProxySerializer<FinalityPayload, FinalityPayloadProxy>(), UsedByFlow, UsedByPersistence, UsedByVerification {

    override val type
        get() = FinalityPayload::class.java

    override val proxyType
        get() = FinalityPayloadProxy::class.java

    override val withInheritance
        // FinalityPayload is final.
        get() = false

    override fun toProxy(obj: FinalityPayload): FinalityPayloadProxy {
        return FinalityPayloadProxy(obj.initialTransaction, obj.transferAdditionalSignatures)
    }

    override fun fromProxy(proxy: FinalityPayloadProxy): FinalityPayload {
        return FinalityPayload(proxy.initialTransaction, proxy.transferAdditionalSignatures, serializationService)
    }
}

data class FinalityPayloadProxy(
    val initialTransaction: UtxoSignedTransactionInternal,
    val transferAdditionalSignatures: Boolean
)