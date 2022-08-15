package net.corda.flow.application.persistence.external.events

import net.corda.data.flow.event.external.ExternalEventContext
import net.corda.data.persistence.EntityRequest
import net.corda.data.persistence.EntityResponse
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.flow.external.events.factory.ExternalEventRecord
import net.corda.flow.state.FlowCheckpoint
import net.corda.schema.Schemas
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.virtualnode.toAvro
import org.osgi.service.component.annotations.Component

@Component(service = [ExternalEventFactory::class])
class PersistenceServiceExternalEventFactory :
    ExternalEventFactory<PersistenceParameters, EntityResponse, ByteArray?> {

    private companion object {
        val log = contextLogger()
    }

    override fun createExternalEvent(
        checkpoint: FlowCheckpoint,
        flowExternalEventContext: ExternalEventContext,
        parameters: PersistenceParameters
    ): ExternalEventRecord {
        log.debug { parameters.debugLog(flowExternalEventContext.requestId) }
        return ExternalEventRecord(
            topic = Schemas.VirtualNode.ENTITY_PROCESSOR,
            payload = EntityRequest.newBuilder()
                .setHoldingIdentity(checkpoint.holdingIdentity.toAvro())
                .setRequest(parameters.request)
                .setFlowExternalEventContext(flowExternalEventContext)
                .build()
        )
    }

    override fun resumeWith(checkpoint: FlowCheckpoint, response: EntityResponse): ByteArray? {
        return response.result?.array()
    }
}