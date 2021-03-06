package net.corda.flow.pipeline.handlers.events

import net.corda.data.persistence.EntityResponse
import net.corda.flow.persistence.manager.PersistenceManager
import net.corda.flow.pipeline.FlowEventContext
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * Handles responses from the db worker for persistence queries.
 */
@Component(service = [FlowEventHandler::class])
class EntityResponseHandler @Activate constructor(
    @Reference(service = PersistenceManager::class)
    private val persistenceManager: PersistenceManager
) : FlowEventHandler<EntityResponse> {

    private companion object {
        val log = contextLogger()
    }

    override val type = EntityResponse::class.java

    override fun preProcess(context: FlowEventContext<EntityResponse>): FlowEventContext<EntityResponse> {
        val checkpoint = context.checkpoint
        val entityResponse = context.inputEventPayload
        log.debug { "Entity response received. Id ${entityResponse.requestId}, result: ${entityResponse.responseType::class.java}" }
        val persistenceState = checkpoint.persistenceState
        if (persistenceState == null) {
            //Duplicate response
            log.warn("Received response but persistenceState in checkpoint was null. requestId ${entityResponse.requestId}")
        } else {
            val updatedPersistenceState = persistenceManager.processMessageReceived(persistenceState, entityResponse)
            checkpoint.persistenceState = updatedPersistenceState
        }
        return context
    }
}
