package net.corda.components.examples.durable.processor

import net.corda.data.demo.DemoRecord
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.contextLogger
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

class DemoDurableProcessor(
    private val outputEventTopic: String,
    private val outputPubSubTopic: String,
    private val killProcessOnRecord: Int? = 0,
    private val delayOnNext: Long = 0L
) :
    DurableProcessor<String, DemoRecord> {

    private companion object {
        val log: Logger = contextLogger()
        val consoleLogger: Logger = LoggerFactory.getLogger("Console")
    }

    var counter = 1
    private var expectedNextValues = mutableMapOf<String, Int>()

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

            if (delayOnNext != 0L) {
                log.error("Durable processor pausing..")
                Thread.sleep(delayOnNext)
            }

            val key = event.key
            val eventRecord = event.value
            val eventRecordValue = eventRecord!!.value
            val newPublisherSet = eventRecordValue == 1
            if (expectedNextValues[key] != null && expectedNextValues[key] != eventRecordValue && !newPublisherSet) {
                log.error("Wrong record found! Expected to find ${expectedNextValues[key]} but found $eventRecordValue")
                consoleLogger.info("Wrong record received by Durable processor! " +
                        "Expected to find ${expectedNextValues[key]} but found $eventRecordValue")
            }
            expectedNextValues[key] = eventRecordValue + 1

            log.info("Durable sub processing key/value  ${key}/${eventRecord}")
            outputRecords.add(Record(outputEventTopic, key, eventRecord))
            outputRecords.add(Record(outputPubSubTopic, key, eventRecord))
            counter++
        }

        return outputRecords
    }
}