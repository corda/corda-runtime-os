package net.corda.flow.pipeline.handlers.requests.persistence

import java.nio.ByteBuffer
import java.time.Instant
import net.corda.data.flow.state.waiting.EntityResponse
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.data.persistence.DeleteEntities
import net.corda.data.persistence.EntityRequest
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.persistence.manager.PersistenceManager
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.handlers.requests.FlowRequestHandler
import net.corda.virtualnode.toAvro
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowRequestHandler::class])
class DeleteRequestHandler @Activate constructor(
    @Reference(service = PersistenceManager::class)
    private val persistenceManager: PersistenceManager
) : FlowRequestHandler<FlowIORequest.Delete> {

    override val type = FlowIORequest.Delete::class.java

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.Delete): WaitingFor {
        return WaitingFor(EntityResponse(request.requestId))
    }

    override fun postProcess(context: FlowEventContext<Any>, request: FlowIORequest.Delete): FlowEventContext<Any> {
        val deleteRequest = DeleteEntities(listOf(ByteBuffer.wrap(request.obj)))
        val checkpoint = context.checkpoint
        val entityRequest = EntityRequest(Instant.now(), checkpoint.flowId, checkpoint.holdingIdentity.toAvro(), deleteRequest)
        context.checkpoint.persistenceState  = persistenceManager.processMessageToSend(request.requestId, entityRequest)
        return context
    }
}
