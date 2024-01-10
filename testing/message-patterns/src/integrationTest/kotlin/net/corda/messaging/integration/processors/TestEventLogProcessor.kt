package net.corda.messaging.integration.processors

import net.corda.data.demo.DemoRecord
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import org.slf4j.LoggerFactory
import java.util.concurrent.CountDownLatch

class TestEventLogProcessor(
    private val latch: CountDownLatch? = null, private val outputTopic: String? = null, val id: String? = null
) : EventLogProcessor<String, DemoRecord> {
    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<DemoRecord>
        get() = DemoRecord::class.java

    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
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
