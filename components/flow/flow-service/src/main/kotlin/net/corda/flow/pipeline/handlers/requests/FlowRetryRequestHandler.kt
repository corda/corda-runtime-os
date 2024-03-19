package net.corda.flow.pipeline.handlers.requests

import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.fiber.cache.FlowFiberCache
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.messaging.api.exception.CordaMessageAPIConsumerResetException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Suppress("Unused")
@Component(service = [FlowRequestHandler::class])
class FlowRetryRequestHandler @Activate constructor(
    @Reference(service = FlowFiberCache::class)
    private val flowFiberCache: FlowFiberCache
) : FlowRequestHandler<FlowIORequest.FlowRetry> {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }
    override val type = FlowIORequest.FlowRetry::class.java

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.FlowRetry): WaitingFor? {
        return null
    }

    override fun postProcess(context: FlowEventContext<Any>, request: FlowIORequest.FlowRetry): FlowEventContext<Any> {
        log.warn("Flow ${context.checkpoint.flowId} requested a retry")
       // flowFiberCache.remove(context.checkpoint.flowKey)
        throw CordaMessageAPIConsumerResetException("Flow ${context.checkpoint.flowId} requested a retry")
    }
}