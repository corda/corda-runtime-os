package net.corda.messaging.kafka.integration.processors

import net.corda.data.demo.DemoRecord
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import java.util.concurrent.CountDownLatch

class TestDurableProcessor(
    private val latch: CountDownLatch, private val outputTopic: String? = null, private val delayProcessor: Long? = null
) : DurableProcessor<String, DemoRecord> {
    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<DemoRecord>
        get() = DemoRecord::class.java

    override fun onNext(events: List<Record<String, DemoRecord>>): List<Record<*, *>> {
        if (delayProcessor != null) {
            Thread.sleep(delayProcessor)
        }

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
