package net.corda.components.examples.persistence.config.admin.processor

import net.corda.components.examples.persistence.config.admin.ConfigState
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import org.slf4j.Logger
import javax.persistence.EntityManagerFactory

class ConfigAdminProcessor(
    private val outputEventTopic: String,
    private val entityManagerFactory: EntityManagerFactory,
    private val logger: Logger,
) :
    DurableProcessor<String, String> {

    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<String>
        get() = String::class.java

    override fun onNext(events: List<Record<String, String>>): List<Record<*, *>> {
        logger.info("Received ${events.map { it.key + "/" + it.value }}")
        val outputRecords = mutableListOf<Record<*, *>>()

        if(events.isEmpty())
            return emptyList()

        logger.info("Update config values")
        val em = entityManagerFactory.createEntityManager()
        em.transaction.begin()

        for (event in events) {
            // TODO: use proper Record
            val key = event.key
            val eventRecord = event.value

            logger.info("Persisting config: $key/$eventRecord")

            val configRecord = ConfigState(key, eventRecord!!)
            em.merge(configRecord)

            // config-state
            outputRecords.add(Record(outputEventTopic, key, eventRecord))
        }

        em.transaction.commit()
        logger.info("Config values committed")

        return outputRecords
    }
}