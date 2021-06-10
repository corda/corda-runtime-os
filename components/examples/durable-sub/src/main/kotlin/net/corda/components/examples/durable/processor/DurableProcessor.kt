package net.corda.components.examples.durable.processor

import net.corda.data.demo.DemoRecord
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class DemoPubSubProcessor(private val outputEventTopic: String, private val outputPubSubTopic: String) :
    DurableProcessor<String, DemoRecord> {

    private companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<DemoRecord>
        get() = DemoRecord::class.java

    override fun onNext(events: List<Record<String, DemoRecord>>): List<Record<*, *>> {
        val outputRecords = mutableListOf<Record<*, *>>()

        for (event in events) {
            log.info("Durable sub processing record ${event.key} - ${event.value}")
            outputRecords.add(Record(outputEventTopic, event.key, event.value))
            outputRecords.add(Record(outputPubSubTopic, event.key, event.value))
        }

        return outputRecords
    }
}