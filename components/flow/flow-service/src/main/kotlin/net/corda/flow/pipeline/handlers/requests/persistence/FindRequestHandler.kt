package net.corda.flow.pipeline.handlers.requests.persistence

import java.nio.ByteBuffer
import java.time.Instant
import net.corda.data.flow.state.waiting.EntityResponse
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.data.persistence.EntityRequest
import net.corda.data.persistence.FindEntity
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.persistence.manager.PersistenceManager
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.handlers.requests.FlowRequestHandler
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowRequestHandler::class])
class FindRequestHandler @Activate constructor(
    @Reference(service = PersistenceManager::class)
    private val persistenceManager: PersistenceManager
) : FlowRequestHandler<FlowIORequest.Find> {

    override val type = FlowIORequest.Find::class.java

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.Find): WaitingFor {
        return WaitingFor(EntityResponse(request.requestId))
    }

    override fun postProcess(context: FlowEventContext<Any>, request: FlowIORequest.Find): FlowEventContext<Any> {
        val findRequest = FindEntity(request.className, ByteBuffer.wrap(request.primaryKey))
        val checkpoint = context.checkpoint
        val entityRequest = EntityRequest(Instant.now(), checkpoint.flowId, checkpoint.holdingIdentity, findRequest)
        return context.apply { checkpoint.persistenceState = persistenceManager.processMessageToSend(request.requestId, entityRequest) }
    }
}
