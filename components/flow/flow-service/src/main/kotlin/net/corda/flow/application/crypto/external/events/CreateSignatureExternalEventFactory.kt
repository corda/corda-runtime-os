package net.corda.flow.application.crypto.external.events

import net.corda.crypto.core.DigitalSignatureWithKey
import net.corda.crypto.flow.CryptoFlowOpsTransformer
import net.corda.data.crypto.wire.ops.flow.FlowOpsResponse
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.flow.external.events.factory.ExternalEventRecord
import net.corda.flow.state.FlowCheckpoint
import net.corda.v5.crypto.SignatureSpec
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [ExternalEventFactory::class])
class CreateSignatureExternalEventFactory @Activate constructor(
    @Reference(service = CryptoFlowOpsTransformer::class)
    private val cryptoFlowOpsTransformer: CryptoFlowOpsTransformer
) : ExternalEventFactory<SignParameters, FlowOpsResponse, DigitalSignatureWithKey> {

    override val responseType = FlowOpsResponse::class.java

    override fun createExternalEvent(
        checkpoint: FlowCheckpoint,
        flowExternalEventContext: ExternalEventContext,
        parameters: SignParameters
    ): ExternalEventRecord {
        TODO("Not implemented")
    }

    override fun resumeWith(checkpoint: FlowCheckpoint, response: FlowOpsResponse): DigitalSignatureWithKey {
        TODO("Not implemented")
    }
}

data class SignParameters(val bytes: ByteArray, val encodedPublicKeyBytes: ByteArray, val signatureSpec: SignatureSpec)