package net.corda.flow.application.crypto

import java.security.PublicKey
import net.corda.crypto.flow.CryptoFlowOpsTransformer
import net.corda.crypto.flow.factory.CryptoFlowOpsTransformerFactory
import net.corda.data.crypto.wire.ops.flow.FlowOpsResponse
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.external.events.handler.ExternalEventHandler
import net.corda.flow.external.events.handler.ExternalEventRecord
import net.corda.flow.state.FlowCheckpoint
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

@Component(service = [SigningService::class, SingletonSerializeAsToken::class], scope = PROTOTYPE)
class SigningServiceImpl @Activate constructor(
    @Reference(service = ExternalEventExecutor::class)
    private val externalEventExecutor: ExternalEventExecutor,
    @Reference(service = KeyEncodingService::class)
    private val keyEncodingService: KeyEncodingService
) : SigningService, SingletonSerializeAsToken {

    @Suspendable
    override fun sign(bytes: ByteArray, publicKey: PublicKey, signatureSpec: SignatureSpec): DigitalSignature.WithKey {
        return externalEventExecutor.execute(
            CreateSignatureExternalEventHandler::class.java,
            SignParameters(bytes, publicKey, signatureSpec)
        )
    }

    @Suspendable
    override fun decodePublicKey(encodedKey: String): PublicKey {
        return keyEncodingService.decodePublicKey(encodedKey)
    }
}

data class SignParameters(val bytes: ByteArray, val publicKey: PublicKey, val signatureSpec: SignatureSpec)

@Component(service = [ExternalEventHandler::class])
class CreateSignatureExternalEventHandler @Activate constructor(
    @Reference(service = CryptoFlowOpsTransformer::class)
    private val cryptoFlowOpsTransformer: CryptoFlowOpsTransformer
) : ExternalEventHandler<SignParameters, FlowOpsResponse, DigitalSignature.WithKey> {

    override fun suspending(
        checkpoint: FlowCheckpoint,
        flowExternalEventContext: ExternalEventContext,
        parameters: SignParameters
    ): ExternalEventRecord {
        val flowOpsRequest = cryptoFlowOpsTransformer.createSign(
            requestId = flowExternalEventContext.requestId,
            tenantId = checkpoint.holdingIdentity.shortHash,
            publicKey = parameters.publicKey,
            signatureSpec = parameters.signatureSpec,
            data = parameters.bytes,
            context = emptyMap(),
            flowExternalEventContext = flowExternalEventContext
        )
        return ExternalEventRecord(Schemas.Crypto.FLOW_OPS_MESSAGE_TOPIC, flowOpsRequest)
    }

    override fun resuming(checkpoint: FlowCheckpoint, response: FlowOpsResponse): DigitalSignature.WithKey {
        return cryptoFlowOpsTransformer.transform(response) as DigitalSignature.WithKey
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