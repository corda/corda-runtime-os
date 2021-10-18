package net.corda.components.examples.persistence.config.admin.processor

import net.corda.components.examples.persistence.config.admin.ConfigState
import net.corda.data.config.Configuration
import net.corda.data.poc.persistence.ConfigAdminEvent
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import org.slf4j.Logger
import javax.persistence.EntityManagerFactory

class ConfigAdminProcessor(
    private val outputEventTopic: String,
    private val entityManagerFactory: EntityManagerFactory,
    private val logger: Logger,
) :
    DurableProcessor<String, ConfigAdminEvent> {

    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<ConfigAdminEvent>
        get() = ConfigAdminEvent::class.java

    override fun onNext(events: List<Record<String, ConfigAdminEvent>>): List<Record<*, *>> {
        logger.info("Received ${events.map { it.key + "/" + it.value }}")
        val outputRecords = mutableListOf<Record<*, *>>()

        if(events.isEmpty())
            return emptyList()

        logger.info("Update config values")
        val em = entityManagerFactory.createEntityManager()
        em.transaction.begin()

        for (event in events) {
            val eventKey = event.key
            val eventRecord = event.value!!
            val configState = ConfigState(eventRecord.key, eventRecord.value, eventRecord.version)
            logger.info("Persisting config (event: $eventKey): $configState")
            em.merge(configState)

            // config-state
            outputRecords.add(Record(outputEventTopic, eventRecord.key,
                Configuration(eventRecord.value, eventRecord.version.toString())))
        }

        em.transaction.commit()
        logger.info("Config values committed")

        return outputRecords
    }
}