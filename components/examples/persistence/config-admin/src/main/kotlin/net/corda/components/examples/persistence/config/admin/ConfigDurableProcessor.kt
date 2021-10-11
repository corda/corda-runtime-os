package net.corda.components.examples.persistence.config.admin

import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.contextLogger
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.sql.Connection

class ConfigDurableProcessor(
    private val outputEventTopic: String,
    private val dbConnection: Connection,
    private val logger: Logger,
) :
    DurableProcessor<String, String> {

    private companion object {
        val log: Logger = contextLogger()
        val consoleLogger: Logger = LoggerFactory.getLogger("Console")
    }

    var counter = 1

    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<String>
        get() = String::class.java

    override fun onNext(events: List<Record<String, String>>): List<Record<*, *>> {
        val outputRecords = mutableListOf<Record<*, *>>()

        // TODO: make this 1 transaction (to Hibernate)
        for (event in events) {

            val key = event.key
            val eventRecord = event.value

            // TODO: Create Hibernate entity mgr and stick the config in there

            log.info("Durable sub processing key/value  ${key}/${eventRecord}")
            // config-state
            outputRecords.add(Record(outputEventTopic, key, eventRecord))
            counter++
        }

        return outputRecords
    }
}