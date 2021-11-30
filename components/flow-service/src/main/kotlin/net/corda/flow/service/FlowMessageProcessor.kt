package net.corda.flow.service

import net.corda.data.flow.Checkpoint
import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.flow.manager.FlowEventExecutorFactory
import net.corda.flow.manager.FlowMetaDataFactory
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.contextLogger

class FlowMessageProcessor(
    private val flowMetaDataFactory: FlowMetaDataFactory,
    private val flowEventExecutorFactory: FlowEventExecutorFactory
) : StateAndEventProcessor<FlowKey, Checkpoint, FlowEvent> {

    companion object {
        private val logger = contextLogger()
    }

    override fun onNext(
        state: Checkpoint?,
        event: Record<FlowKey, FlowEvent>
    ): StateAndEventProcessor.Response<Checkpoint> {

        val metaData = flowMetaDataFactory.createFromEvent(state, event)
        val executor = flowEventExecutorFactory.create(metaData)
        val result = executor.execute()

        return StateAndEventProcessor.Response(result.checkpoint, result.events)
    }

    override val keyClass = FlowKey::class.java
    override val stateValueClass = Checkpoint::class.java
    override val eventValueClass = FlowEvent::class.java
}
