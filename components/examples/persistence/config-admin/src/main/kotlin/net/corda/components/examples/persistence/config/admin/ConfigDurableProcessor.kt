package net.corda.components.examples.persistence.config.admin

import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import org.slf4j.Logger
import javax.persistence.EntityManagerFactory

class ConfigDurableProcessor(
    private val outputEventTopic: String,
    private val entityManagerFactory: EntityManagerFactory,
    private val logger: Logger,
) :
    DurableProcessor<String, String> {

    var counter = 1

    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<String>
        get() = String::class.java

    override fun onNext(events: List<Record<String, String>>): List<Record<*, *>> {
        val outputRecords = mutableListOf<Record<*, *>>()

        val em = entityManagerFactory.createEntityManager()

        em.transaction.begin()

        for (event in events) {

            val key = event.key
            val eventRecord = event.value

            val configRecord = ConfigState(key, eventRecord!!)
            em.persist(configRecord)

            logger.info("Durable sub processing key/value  ${key}/${eventRecord}")
            // config-state
            outputRecords.add(Record(outputEventTopic, key, eventRecord))
            counter++
        }

        em.transaction.commit()

        return outputRecords
    }
}