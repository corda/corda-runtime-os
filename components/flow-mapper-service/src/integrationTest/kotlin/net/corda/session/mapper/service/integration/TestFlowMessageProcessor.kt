package net.corda.session.mapper.service.integration

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.Checkpoint
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import java.util.concurrent.CountDownLatch

class TestFlowMessageProcessor(
    private val latch: CountDownLatch,
) : StateAndEventProcessor<FlowKey, Checkpoint, FlowEvent> {

    override fun onNext(
        state: Checkpoint?,
        event: Record<FlowKey, FlowEvent>
    ): StateAndEventProcessor.Response<Checkpoint> {
        latch.countDown()
        return StateAndEventProcessor.Response(state, emptyList())
    }

    override val keyClass = FlowKey::class.java
    override val stateValueClass = Checkpoint::class.java
    override val eventValueClass = FlowEvent::class.java
}
