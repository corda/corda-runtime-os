package net.corda.components.examples.negatingdurable.processor

import net.corda.data.demo.DemoRecord
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.contextLogger
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

class TestNegatingDurableProcessor(
    private val outputPubSubTopic: String,
    private val delayOnNext: Long = 0L
) :
    DurableProcessor<String, DemoRecord> {

    private companion object {
        val log: Logger = contextLogger()
        //val consoleLogger: Logger = LoggerFactory.getLogger("Console")
    }

    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<DemoRecord>
        get() = DemoRecord::class.java

    override fun onNext(events: List<Record<String, DemoRecord>>): List<Record<*, *>> {
        val outputRecords = mutableListOf<Record<*, *>>()

        for (event in events) {
            if (delayOnNext != 0L) {
                log.error("Durable processor pausing..")
                Thread.sleep(delayOnNext)
            }

            val key = event.key
            val eventRecord = event.value // This is a DemoRecord
            //val eventRecordValue = eventRecord!!.value
            //eventRecord!!.value( eventRecordValue * -1)
            val outEventRecord = DemoRecord(- eventRecord!!.value)
            log.info("Negating durable sub processing key/value  ${key}/${eventRecord} -->  ${key}/${outEventRecord}")
            outputRecords.add(Record(outputPubSubTopic, key, outEventRecord))
        }

        return outputRecords
    }
}