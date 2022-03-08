package net.corda.session.mapper.service.executor

import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.flow.mapper.factory.FlowMapperEventExecutorFactory
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.trace

class FlowMapperMessageProcessor(
    private val flowMapperEventExecutorFactory: FlowMapperEventExecutorFactory
) : StateAndEventProcessor<String, FlowMapperState, FlowMapperEvent> {

    companion object {
        private val logger = contextLogger()
    }

    override fun onNext(
        state: FlowMapperState?,
        event: Record<String, FlowMapperEvent>
    ): StateAndEventProcessor.Response<FlowMapperState> {
        val key = event.key
        logger.trace { "Received event: key: $key event: $event "}
        val value = event.value ?: return StateAndEventProcessor.Response(state, emptyList())
        val executor = flowMapperEventExecutorFactory.create(key, value, state)
        val result = executor.execute()

        return StateAndEventProcessor.Response(result.flowMapperState, result.outputEvents)
    }

    override val keyClass = String::class.java
    override val stateValueClass = FlowMapperState::class.java
    override val eventValueClass = FlowMapperEvent::class.java
}
