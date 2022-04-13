package net.corda.session.mapper.service.integration

import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.Checkpoint
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import org.junit.jupiter.api.fail
import java.util.concurrent.CountDownLatch

class TestFlowMessageProcessor(
    private val latch: CountDownLatch,
    private val expectedRecordCount: Int,
    private val expectedType: Class<*>
) : StateAndEventProcessor<String, Checkpoint, FlowEvent> {

    private var recordCount = 0

    var eventsReceived: MutableList<Record<String, FlowEvent>> = mutableListOf()

    override fun onNext(
        state: Checkpoint?,
        event: Record<String, FlowEvent>
    ): StateAndEventProcessor.Response<Checkpoint> {
        eventsReceived.add(event)

        /**
         * This change was needed due to shared state across tests. Expected type works for now, but we need a more
         * robust way to assert the expected output without.
         */
        val payloadClass = event.value?.payload?.javaClass
        if (payloadClass == expectedType) {
            recordCount++
            if (recordCount > expectedRecordCount) {
                fail("Expected record count exceeded in events processed for this identity")
            }
            latch.countDown()
        }
        return StateAndEventProcessor.Response(state, emptyList())
    }

    override val keyClass = String::class.java
    override val stateValueClass = Checkpoint::class.java
    override val eventValueClass = FlowEvent::class.java
}
