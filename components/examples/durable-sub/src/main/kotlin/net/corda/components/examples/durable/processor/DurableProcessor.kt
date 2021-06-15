package net.corda.components.examples.durable.processor

import net.corda.data.demo.DemoRecord
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

class DemoPubSubProcessor(
    private val outputEventTopic: String,
    private val outputPubSubTopic: String,
    private val killProcessOnRecord: Int? = 0
) :
    DurableProcessor<String, DemoRecord> {

    private companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    var counter = 1

    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<DemoRecord>
        get() = DemoRecord::class.java

    override fun onNext(events: List<Record<String, DemoRecord>>): List<Record<*, *>> {
        val outputRecords = mutableListOf<Record<*, *>>()

        for (event in events) {
            if (counter == killProcessOnRecord) {
                log.error("Killing process for test purposes!")
                exitProcess(0)
            }
            log.info("Durable sub processing key/value  ${event.key}/${event.value}")
            outputRecords.add(Record(outputEventTopic, event.key, event.value))
            outputRecords.add(Record(outputPubSubTopic, event.key, event.value))
            counter++
        }

        return outputRecords
    }
}