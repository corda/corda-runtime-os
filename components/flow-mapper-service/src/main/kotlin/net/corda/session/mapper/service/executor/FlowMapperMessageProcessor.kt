package net.corda.session.mapper.service.executor

import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.flow.mapper.FlowMapperTopics
import net.corda.flow.mapper.factory.FlowMapperEventExecutorFactory
import net.corda.flow.mapper.factory.FlowMapperMetaDataFactory
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.trace

class FlowMapperMessageProcessor(
    private val flowMetaDataFactory: FlowMapperMetaDataFactory,
    private val flowMapperEventExecutorFactory: FlowMapperEventExecutorFactory,
    private val flowMapperTopics: FlowMapperTopics,
) : StateAndEventProcessor<String, FlowMapperState, FlowMapperEvent> {

    companion object {
        private val logger = contextLogger()
    }

    override fun onNext(
        state: FlowMapperState?,
        event: Record<String, FlowMapperEvent>
    ): StateAndEventProcessor.Response<FlowMapperState> {
        logger.trace { "Received event: key: ${event.key} event: $event "}
        val metaData = flowMetaDataFactory.createFromEvent(flowMapperTopics, state, event)
        val executor = flowMapperEventExecutorFactory.create(metaData)
        val result = executor.execute()

        return StateAndEventProcessor.Response(result.flowMapperState, result.outputEvents)
    }

    override val keyClass = String::class.java
    override val stateValueClass = FlowMapperState::class.java
    override val eventValueClass = FlowMapperEvent::class.java
}
