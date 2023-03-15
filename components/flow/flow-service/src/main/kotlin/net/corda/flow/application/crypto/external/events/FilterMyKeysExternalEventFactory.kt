package net.corda.flow.application.crypto.external.events

import net.corda.crypto.flow.CryptoFlowOpsTransformer
import net.corda.data.crypto.wire.ops.flow.FlowOpsResponse
import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.flow.external.events.factory.ExternalEventRecord
import net.corda.flow.state.FlowCheckpoint
import net.corda.schema.Schemas
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.security.PublicKey

@Component(service = [ExternalEventFactory::class])
class FilterMyKeysExternalEventFactory @Activate constructor(
    @Reference(service = CryptoFlowOpsTransformer::class)
    private val cryptoFlowOpsTransformer: CryptoFlowOpsTransformer
) : ExternalEventFactory<Collection<PublicKey>, FlowOpsResponse, List<PublicKey>> {
    override val responseType: Class<FlowOpsResponse> = FlowOpsResponse::class.java

    override fun createExternalEvent(
        checkpoint: FlowCheckpoint,
        flowExternalEventContext: ExternalEventContext,
        parameters: Collection<PublicKey>
    ): ExternalEventRecord {
        val flowOpsRequest =
            cryptoFlowOpsTransformer
                .createFilterMyKeys(
                    tenantId = checkpoint.holdingIdentity.shortHash.value,
                    candidateKeys = parameters,
                    flowExternalEventContext = flowExternalEventContext
                )
        return ExternalEventRecord(topic = Schemas.Crypto.FLOW_OPS_MESSAGE_TOPIC, payload = flowOpsRequest)
    }

    override fun resumeWith(checkpoint: FlowCheckpoint, response: FlowOpsResponse): List<PublicKey> {
        @Suppress("unchecked_cast")
        return cryptoFlowOpsTransformer.transform(response) as List<PublicKey>
    }
}