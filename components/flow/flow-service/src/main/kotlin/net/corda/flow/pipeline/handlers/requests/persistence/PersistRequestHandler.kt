package net.corda.flow.pipeline.handlers.requests.persistence

import java.nio.ByteBuffer
import java.time.Instant
import net.corda.data.flow.state.waiting.EntityResponse
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.data.persistence.EntityRequest
import net.corda.data.persistence.PersistEntity
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.persistence.manager.PersistenceManager
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.handlers.requests.FlowRequestHandler
import net.corda.virtualnode.toAvro
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowRequestHandler::class])
class PersistRequestHandler @Activate constructor(
    @Reference(service = PersistenceManager::class)
    private val persistenceManager: PersistenceManager
) : FlowRequestHandler<FlowIORequest.Persist> {

    override val type = FlowIORequest.Persist::class.java

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.Persist): WaitingFor {
        return WaitingFor(EntityResponse(request.requestId))
    }

    override fun postProcess(context: FlowEventContext<Any>, request: FlowIORequest.Persist): FlowEventContext<Any> {
        val persistRequest = PersistEntity(ByteBuffer.wrap(request.obj))
        val checkpoint = context.checkpoint
        val entityRequest = EntityRequest(Instant.now(), checkpoint.flowId, checkpoint.holdingIdentity.toAvro(), persistRequest)
        return context.apply { checkpoint.persistenceState = persistenceManager.processMessageToSend(request.requestId, entityRequest) }
    }
}
