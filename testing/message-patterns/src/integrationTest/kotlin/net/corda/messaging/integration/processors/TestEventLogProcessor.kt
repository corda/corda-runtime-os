package net.corda.messaging.integration.processors

import net.corda.data.demo.DemoRecord
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import java.util.concurrent.CountDownLatch

class TestEventLogProcessor(
    private val latch: CountDownLatch, private val outputTopic: String? = null
) : EventLogProcessor<String, DemoRecord> {
    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<DemoRecord>
        get() = DemoRecord::class.java

    override fun onNext(events: List<EventLogRecord<String, DemoRecord>>): List<Record<*, *>> {
        for (event in events) {
            latch.countDown()
        }

        return if (outputTopic != null) {
            listOf(Record(outputTopic, "durableOutputKey", DemoRecord(1)))
        } else {
            emptyList<Record<String, DemoRecord>>()
        }
    }
}
