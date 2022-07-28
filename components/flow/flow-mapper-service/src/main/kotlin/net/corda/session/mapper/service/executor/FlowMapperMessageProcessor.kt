package net.corda.session.mapper.service.executor

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.flow.mapper.factory.FlowMapperEventExecutorFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug

/**
 * The [FlowMapperMessageProcessor] receives states and events that are keyed by strings. These strings can be either:
 *
 * - `toString`ed [FlowKey]s representing flow starts.
 * - Session ids representing sessions.
 */
class FlowMapperMessageProcessor(
    private val flowMapperEventExecutorFactory: FlowMapperEventExecutorFactory,
    private val flowConfig: SmartConfig
) : StateAndEventProcessor<String, FlowMapperState, FlowMapperEvent> {

    companion object {
        private val logger = contextLogger()
    }

    override fun onNext(
        state: FlowMapperState?,
        event: Record<String, FlowMapperEvent>
    ): StateAndEventProcessor.Response<FlowMapperState> {
        val key = event.key
        logger.debug { "Received event: key: $key event: ${event.value}" }
        val value = event.value ?: return StateAndEventProcessor.Response(state, emptyList())
        val executor = flowMapperEventExecutorFactory.create(key, value, state, flowConfig)
        val result = executor.execute()

        //propagate context
        result.outputEvents.forEach {
            it.context = event.context
        }

        return StateAndEventProcessor.Response(result.flowMapperState, result.outputEvents)
    }

    override val keyClass = String::class.java
    override val stateValueClass = FlowMapperState::class.java
    override val eventValueClass = FlowMapperEvent::class.java
}
