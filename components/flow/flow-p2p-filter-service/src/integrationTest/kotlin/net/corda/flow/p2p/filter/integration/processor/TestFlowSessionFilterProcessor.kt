package net.corda.flow.p2p.filter.integration.processor

import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import org.junit.jupiter.api.fail
import java.util.concurrent.CountDownLatch

class TestFlowSessionFilterProcessor(
    private val key: String,
    private val latch: CountDownLatch,
    private val expectedRecordCount: Int
) : DurableProcessor<String, FlowMapperEvent> {

    override val keyClass = String::class.java
    override val valueClass = FlowMapperEvent::class.java

    private var recordCount = 0

    override fun onNext(events: List<Record<String, FlowMapperEvent>>): List<Record<*, *>> {
        for (event in events) {
            if (event.key == key) {
                recordCount++
                if (recordCount > expectedRecordCount) {
                    fail("Expected record count exceeded in events processed for this key")
                }
                latch.countDown()
            }
        }

        return emptyList()
    }
}
