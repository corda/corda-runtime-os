package net.corda.flow.pipeline.handlers.requests.db

import java.nio.ByteBuffer
import java.time.Instant
import net.corda.data.flow.state.waiting.EntityResponse
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.data.persistence.EntityRequest
import net.corda.data.persistence.FindEntity
import net.corda.flow.db.manager.DbManager
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.handlers.requests.FlowRequestHandler
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowRequestHandler::class])
class FindRequestHandler @Activate constructor(
    @Reference(service = DbManager::class)
    private val dbManager: DbManager
) : FlowRequestHandler<FlowIORequest.Find> {

    override val type = FlowIORequest.Find::class.java

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.Find): WaitingFor {
        return WaitingFor(EntityResponse(request.requestId))
    }

    override fun postProcess(context: FlowEventContext<Any>, request: FlowIORequest.Find): FlowEventContext<Any> {
        val findRequest = FindEntity(request.className, ByteBuffer.wrap(request.primaryKey))
        val entityRequest = EntityRequest(Instant.now(), context.checkpoint.flowKey, findRequest)
        return context.apply { checkpoint.query = dbManager.processMessageToSend(request.requestId, entityRequest) }
    }
}
