package net.corda.flow.pipeline.handlers.requests.persistence

import java.nio.ByteBuffer
import java.time.Instant
import net.corda.data.flow.state.waiting.EntityResponse
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.data.persistence.EntityRequest
import net.corda.data.persistence.MergeEntity
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.persistence.manager.PersistenceManager
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.handlers.requests.FlowRequestHandler
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowRequestHandler::class])
class MergeRequestHandler @Activate constructor(
    @Reference(service = PersistenceManager::class)
    private val persistenceManager: PersistenceManager
) : FlowRequestHandler<FlowIORequest.Merge> {

    override val type = FlowIORequest.Merge::class.java

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.Merge): WaitingFor {
        return WaitingFor(EntityResponse(request.requestId))
    }

    override fun postProcess(context: FlowEventContext<Any>, request: FlowIORequest.Merge): FlowEventContext<Any> {
        val persistRequest = MergeEntity(ByteBuffer.wrap(request.obj))
        val checkpoint = context.checkpoint
        val entityRequest = EntityRequest(Instant.now(), checkpoint.flowId, checkpoint.holdingIdentity, persistRequest)
        return context.apply { checkpoint.persistenceState = persistenceManager.processMessageToSend(request.requestId, entityRequest) }
    }
}
