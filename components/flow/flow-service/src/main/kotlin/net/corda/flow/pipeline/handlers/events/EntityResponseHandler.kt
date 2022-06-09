package net.corda.flow.pipeline.handlers.events

import net.corda.data.persistence.EntityResponse
import net.corda.flow.db.manager.DbManager
import net.corda.flow.pipeline.FlowEventContext
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowEventHandler::class])
class EntityResponseHandler @Activate constructor(
    @Reference(service = DbManager::class)
    private val dbManager: DbManager
) : FlowEventHandler<EntityResponse> {

    private companion object {
        val log = contextLogger()
    }

    override val type = EntityResponse::class.java

    override fun preProcess(context: FlowEventContext<EntityResponse>): FlowEventContext<EntityResponse> {
        val checkpoint = context.checkpoint
        val entityResponse = context.inputEventPayload
        log.debug { "Entity response: ${entityResponse.responseType::class.java}" }
        val query = checkpoint.query
        if (query == null) {
            //TODO - error handling
            log.error("Query was null")
        } else {
            val updatedQuery = dbManager.processMessageReceived(query, entityResponse)
            checkpoint.query = updatedQuery
        }
        return context
    }
}
