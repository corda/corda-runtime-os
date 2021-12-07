package net.corda.processors.db.internal

import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.contextLogger
import javax.persistence.EntityManager

internal class DBCompactedProcessor(
    private val publisher: Publisher,
    private val entityManager: EntityManager
) : CompactedProcessor<String, String> {

    private companion object {
        val logger = contextLogger()
    }

    override val keyClass = String::class.java
    override val valueClass = String::class.java

    override fun onSnapshot(currentData: Map<String, String>) = Unit

    override fun onNext(newRecord: Record<String, String>, oldValue: String?, currentData: Map<String, String>) {
        logger.info("jjj processing update in dbcompactedprocessor: $newRecord")

        // TODO - Joel - Send config to DB.
        entityManager.transaction.begin()
        entityManager.persist(ConfigEntity("a", "b"))
        entityManager.transaction.commit()

        publisher.publish(listOf(Record("config", newRecord.key, newRecord.value)))
    }
}