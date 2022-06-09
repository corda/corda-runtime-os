package net.corda.flow.pipeline.handlers.waiting.db


import net.corda.data.flow.state.waiting.EntityResponse
import net.corda.data.persistence.DeleteEntity
import net.corda.data.persistence.EntityRequest
import net.corda.data.persistence.EntityResponseSuccess
import net.corda.data.persistence.FindEntity
import net.corda.data.persistence.MergeEntity
import net.corda.data.persistence.PersistEntity
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.handlers.waiting.FlowWaitingForHandler
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.osgi.service.component.annotations.Component

@Component(service = [FlowWaitingForHandler::class])
class EntityResponseWaitingForHandler : FlowWaitingForHandler<EntityResponse> {

    private companion object {
        val log = contextLogger()
    }

    override val type = EntityResponse::class.java

    override fun runOrContinue(context: FlowEventContext<*>, waitingFor: EntityResponse): FlowContinuation {
        val checkpoint = context.checkpoint
        val query = checkpoint.query
        if (query?.request?.request != null) {
            log.debug { "Check for response for request type ${query.request.request::class} and id ${query.requestId}" }
        }
        val response = query?.response
        return if (response != null) {
            val responseType = response.responseType
            log.debug { "Response received of type ${response.responseType::class} for request id ${response.requestId}" }
            if (responseType is EntityResponseSuccess) {
                val entityResponse = getPayloadFromResponse(query.request, responseType)
                context.checkpoint.query = null
                entityResponse
            } else {
                //TODO - error handling
                context.checkpoint.query = null
                FlowContinuation.Continue
            }
        } else {
            FlowContinuation.Continue
        }
    }

    private fun getPayloadFromResponse(request: EntityRequest, response: EntityResponseSuccess): FlowContinuation {
        return when (request.request) {
            is FindEntity,
            is MergeEntity -> {
                FlowContinuation.Run(response.result)
            }
            is DeleteEntity,
            is PersistEntity -> {
                FlowContinuation.Run(Unit)
            }
            else -> {
                FlowContinuation.Continue
            }
        }
    }
}
