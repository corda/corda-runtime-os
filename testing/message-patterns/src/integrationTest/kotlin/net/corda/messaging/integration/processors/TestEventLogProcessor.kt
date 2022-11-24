package net.corda.messaging.integration.processors

import java.util.concurrent.CountDownLatch
import net.corda.data.demo.DemoRecord
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.contextLogger

class TestEventLogProcessor(
    private val latch: CountDownLatch? = null, private val outputTopic: String? = null, val id: String? = null
) : EventLogProcessor<String, DemoRecord> {
    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<DemoRecord>
        get() = DemoRecord::class.java

    private companion object {
        val logger = contextLogger()
    }

    override fun onNext(events: List<EventLogRecord<String, DemoRecord>>): List<Record<*, *>> {
        val outputRecords = mutableListOf<Record<*, *>>()
        for (event in events) {
            logger.info("TestEventLogProcessor $id for output topic $outputTopic processing event $event")
            latch?.countDown()
            if (outputTopic != null) {
                outputRecords.add(Record(outputTopic, event.key, event.value))
            }
        }

        return outputRecords
    }
}
