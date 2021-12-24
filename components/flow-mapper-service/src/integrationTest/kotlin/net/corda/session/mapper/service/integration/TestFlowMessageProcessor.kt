package net.corda.session.mapper.service.integration

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.Checkpoint
import net.corda.data.identity.HoldingIdentity
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import org.junit.jupiter.api.fail
import java.util.concurrent.CountDownLatch

class TestFlowMessageProcessor(
    private val latch: CountDownLatch,
    private val expectedIdentity: HoldingIdentity,
    private val expectedRecordCount: Int
    ) : StateAndEventProcessor<FlowKey, Checkpoint, FlowEvent> {

    private var recordCount = 0

    override fun onNext(
        state: Checkpoint?,
        event: Record<FlowKey, FlowEvent>
    ): StateAndEventProcessor.Response<Checkpoint> {
        if (event.key.identity == expectedIdentity) {
            recordCount++
            if (recordCount > expectedRecordCount) {
                fail("Expected record count exceeded in events processed for this identity")
            }
            latch.countDown()
        }
        return StateAndEventProcessor.Response(state, emptyList())
    }

    override val keyClass = FlowKey::class.java
    override val stateValueClass = Checkpoint::class.java
    override val eventValueClass = FlowEvent::class.java
}
