package net.corda.crypto.persistence.kafka

import net.corda.crypto.component.persistence.EntityKeyInfo
import net.corda.crypto.impl.closeGracefully
import net.corda.data.crypto.persistence.SoftKeysRecord
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.schema.Schemas
import net.corda.v5.base.util.contextLogger
import org.slf4j.Logger
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class KafkaSoftPersistenceProcessor(
    subscriptionFactory: SubscriptionFactory,
    publisherFactory: PublisherFactory
) : CompactedProcessor<String, SoftKeysRecord>, KafkaPersistenceProcessor<SoftKeysRecord> {
    companion object {
        private val logger: Logger = contextLogger()
        const val GROUP_NAME = "crypto.key.soft"
        const val CLIENT_ID = "crypto.key.soft"
    }

    private var keyMap = ConcurrentHashMap<String, SoftKeysRecord>()

    private val pub: Publisher = publisherFactory.createPublisher(
        PublisherConfig(CLIENT_ID)
    )

    private val sub: CompactedSubscription<String, SoftKeysRecord> =
        subscriptionFactory.createCompactedSubscription(
            SubscriptionConfig(GROUP_NAME, Schemas.Crypto.SOFT_HSM_PERSISTENCE_TOPIC),
            this
        ).also { it.start() }

    override val keyClass: Class<String> = String::class.java

    override val valueClass: Class<SoftKeysRecord> = SoftKeysRecord::class.java

    override fun onSnapshot(currentData: Map<String, SoftKeysRecord>) {
        logger.debug("Processing snapshot of {} items", currentData.size)
        val map = ConcurrentHashMap<String, SoftKeysRecord>()
        for (record in currentData) {
            map[record.key] = record.value
        }
        keyMap = map
    }

    override fun onNext(
        newRecord: Record<String, SoftKeysRecord>,
        oldValue: SoftKeysRecord?,
        currentData: Map<String, SoftKeysRecord>
    ) {
        logger.debug("Processing new update")
        if (newRecord.value == null) {
            keyMap.remove(newRecord.key)
        } else {
            keyMap[newRecord.key] = newRecord.value!!
        }
    }

    override fun publish(
        entity: SoftKeysRecord,
        vararg key: EntityKeyInfo
    ): List<CompletableFuture<Unit>> {
        require(key.isNotEmpty()) {
            "There must be at least one key provided."
        }
        logger.debug("Publishing a record '{}' with key='{}'", valueClass.name, key)
        val records = key.map {
            Record(Schemas.Crypto.SOFT_HSM_PERSISTENCE_TOPIC, it.key, entity)
        }
        return pub.publish(records)
    }

    override fun getValue(tenantId: String, key: String): SoftKeysRecord? {
        logger.debug("Requesting a record '{}' with key='{}' for member='{}'", valueClass.name, key, tenantId)
        val value = keyMap[key]
        return if (value == null || value.tenantId != tenantId) {
            if (value != null) {
                logger.warn(
                    "The requested record '{}' with key='{}' for member='{}' is actually for '{}' member",
                    valueClass.name,
                    key,
                    tenantId,
                    value.tenantId
                )
            }
            null
        } else {
            value
        }
    }

    override fun close() {
        pub.closeGracefully()
        sub.closeGracefully()
    }
}