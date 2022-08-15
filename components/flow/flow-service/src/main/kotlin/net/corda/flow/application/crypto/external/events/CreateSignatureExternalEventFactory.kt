package net.corda.flow.application.crypto.external.events

import net.corda.crypto.flow.CryptoFlowOpsTransformer
import net.corda.data.crypto.wire.ops.flow.FlowOpsResponse
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.flow.external.events.factory.ExternalEventRecord
import net.corda.flow.state.FlowCheckpoint
import net.corda.schema.Schemas
import net.corda.v5.crypto.DigitalSignature
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [ExternalEventFactory::class])
class CreateSignatureExternalEventFactory @Activate constructor(
    @Reference(service = CryptoFlowOpsTransformer::class)
    private val cryptoFlowOpsTransformer: CryptoFlowOpsTransformer
) : ExternalEventFactory<SignParameters, FlowOpsResponse, DigitalSignature.WithKey> {

    override fun createExternalEvent(
        checkpoint: FlowCheckpoint,
        flowExternalEventContext: ExternalEventContext,
        parameters: SignParameters
    ): ExternalEventRecord {
        val flowOpsRequest = cryptoFlowOpsTransformer.createSign(
            requestId = flowExternalEventContext.requestId,
            tenantId = checkpoint.holdingIdentity.shortHash.value,
            publicKey = parameters.publicKey,
            signatureSpec = parameters.signatureSpec,
            data = parameters.bytes,
            context = emptyMap(),
            flowExternalEventContext = flowExternalEventContext
        )
        return ExternalEventRecord(topic = Schemas.Crypto.FLOW_OPS_MESSAGE_TOPIC, payload = flowOpsRequest)
    }

    override fun resumeWith(checkpoint: FlowCheckpoint, response: FlowOpsResponse): DigitalSignature.WithKey {
        return cryptoFlowOpsTransformer.transform(response) as DigitalSignature.WithKey
    }
}