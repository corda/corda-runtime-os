package net.corda.components.examples.persistence.config.admin.processor

import net.corda.components.examples.persistence.config.admin.ConfigState
import net.corda.data.config.Configuration
import net.corda.data.poc.persistence.ConfigAdminEvent
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.orm.utils.transaction
import org.slf4j.Logger
import javax.persistence.EntityManagerFactory

class ConfigAdminProcessor(
    private val outputEventTopic: String,
    private val entityManagerFactory: EntityManagerFactory,
    private val logger: Logger,
) :
    DurableProcessor<String, ConfigAdminEvent> {

    override val keyClass = String::class.java
    override val valueClass = ConfigAdminEvent::class.java

    override fun onNext(events: List<Record<String, ConfigAdminEvent>>): List<Record<*, *>> {
        logger.info("Received ${events.map { it.key + "/" + it.value }}")
        val outputRecords = mutableListOf<Record<*, *>>()

        if (events.isEmpty())
            return emptyList()

        logger.info("Update config values")
        entityManagerFactory.transaction { em ->

            for (event in events) {
                val eventKey = event.key
                val eventRecord = event.value
                if (null != eventRecord) {
                    val configState = ConfigState(eventRecord.key, eventRecord.value, eventRecord.version)
                    logger.info("Persisting config (event: $eventKey): $configState")
                    em.merge(configState)

                    // config-state
                    outputRecords.add(
                        Record(
                            outputEventTopic, eventRecord.key,
                            Configuration(eventRecord.value, eventRecord.version.toString())
                        )
                    )
                } else {
                    @Suppress("ForbiddenComment")
                    // TODO: what do we do with "un-processable" messages?
                    //  skipping for now, but need a longer term solution to this
                    logger.error("Skipping Null message for $eventKey.")
                }
            }
        }
        logger.info("Config values committed")

        return outputRecords
    }
}