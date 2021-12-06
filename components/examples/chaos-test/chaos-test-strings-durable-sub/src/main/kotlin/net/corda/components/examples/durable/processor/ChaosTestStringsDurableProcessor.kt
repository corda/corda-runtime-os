package net.corda.components.examples.durable.processor

import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.contextLogger
import org.slf4j.Logger
import kotlin.system.exitProcess

class ChaosTestStringsDurableProcessor(
    private val outputEventTopic: String,
    private val outputPubSubTopic: String,
    private val killProcessOnRecord: Int? = 0,
    private val delayOnNext: Long = 0L
) :
    DurableProcessor<String, String> {

    private companion object {
        val log: Logger = contextLogger()
    }

    var counter = 1

    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<String>
        get() = String::class.java

    override fun onNext(events: List<Record<String, String>>): List<Record<*, *>> {
        val outputRecords = mutableListOf<Record<*, *>>()

        for (event in events) {
            if (counter == killProcessOnRecord) {
                log.info("Killing process for test purposes!")
                exitProcess(0)
            }

            if (delayOnNext != 0L) {
                log.info("Durable processor pausing..")
                Thread.sleep(delayOnNext)
            }

            val key = event.key
            val eventRecord = event.value
            val newEventRecord = "durable-processed(${event.value})"

            log.info("Durable sub processing key/value ${key}/(in:${eventRecord},out:${newEventRecord})")
            outputRecords.add(Record(outputEventTopic, key, newEventRecord))
            outputRecords.add(Record(outputPubSubTopic, key, newEventRecord))
        }

        return outputRecords
    }
}