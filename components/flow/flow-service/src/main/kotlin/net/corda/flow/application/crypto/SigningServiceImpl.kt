package net.corda.flow.application.crypto

import net.corda.crypto.flow.CryptoFlowOpsTransformer
import net.corda.crypto.flow.factory.CryptoFlowOpsTransformerFactory
import net.corda.data.crypto.wire.ops.flow.FlowOpsResponse
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.pipeline.handlers.events.ExternalEventRequest
import net.corda.schema.Schemas
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.cipher.suite.KeyEncodingService
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import java.security.PublicKey
import java.util.UUID

@Component(service = [SigningService::class, SingletonSerializeAsToken::class], scope = PROTOTYPE)
class SigningServiceImpl @Activate constructor(
    @Reference(service = FlowFiberService::class)
    private val flowFiberService: FlowFiberService,
    @Reference(service = KeyEncodingService::class)
    private val keyEncodingService: KeyEncodingService,
    @Reference(service = CryptoFlowOpsTransformer::class)
    private val cryptoFlowOpsTransformer: CryptoFlowOpsTransformer
) : SigningService, SingletonSerializeAsToken {

    @Suspendable
    override fun sign(bytes: ByteArray, publicKey: PublicKey, signatureSpec: SignatureSpec): DigitalSignature.WithKey {
        val holdingIdentity = flowFiberService.getExecutingFiber().getExecutionContext().holdingIdentity.id
        val response = flowFiberService.getExecutingFiber().suspend(
            ExternalEventRequest { requestId ->
                val flowOpsRequest = cryptoFlowOpsTransformer.createSign(
                    requestId = requestId,
                    tenantId = holdingIdentity,
                    publicKey = publicKey,
                    signatureSpec = signatureSpec,
                    data = bytes,
                    context = emptyMap()
                )
                ExternalEventRequest.EventRecord(Schemas.Crypto.FLOW_OPS_MESSAGE_TOPIC, flowOpsRequest)
            }
        )
        return cryptoFlowOpsTransformer.transform(response.lastEventAs()) as DigitalSignature.WithKey
    }

    @Suspendable
    override fun decodePublicKey(encodedKey: String): PublicKey {
        return keyEncodingService.decodePublicKey(encodedKey)
    }
}

@Component(service = [CryptoFlowOpsTransformer::class])
class CryptoFlowOpsTransformerService @Activate constructor(
    @Reference(service = CryptoFlowOpsTransformerFactory::class)
    cryptoFlowOpsTransformerFactory: CryptoFlowOpsTransformerFactory,
) : CryptoFlowOpsTransformer by cryptoFlowOpsTransformerFactory.create(
    requestingComponent = "Flow worker",
    responseTopic = Schemas.Flow.FLOW_EVENT_TOPIC,
    requestValidityWindowSeconds = 300
)