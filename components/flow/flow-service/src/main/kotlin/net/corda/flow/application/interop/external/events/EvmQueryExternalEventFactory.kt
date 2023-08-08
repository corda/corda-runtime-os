package net.corda.flow.application.interop.external.events

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.interop.evm.EvmRequest
import net.corda.data.interop.evm.EvmResponse
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.flow.external.events.factory.ExternalEventRecord
import net.corda.flow.state.FlowCheckpoint
import net.corda.schema.Schemas
import org.osgi.service.component.annotations.Component

data class EvmExternalEventParams(
    val payload: EvmRequest,
)

@Component(service = [ExternalEventFactory::class])
class EvmQueryExternalEventFactory : ExternalEventFactory<EvmExternalEventParams, EvmResponse, Any> {
    override val responseType: Class<EvmResponse> = EvmResponse::class.java

    override fun resumeWith(checkpoint: FlowCheckpoint, response: EvmResponse): Any {
        return response.payload
    }

    override fun createExternalEvent(
        checkpoint: FlowCheckpoint,
        flowExternalEventContext: ExternalEventContext,
        parameters: EvmExternalEventParams
    ): ExternalEventRecord {
        return ExternalEventRecord(
            topic = Schemas.Interop.EVM_REQUEST,
            payload = parameters.payload
        )
    }
}