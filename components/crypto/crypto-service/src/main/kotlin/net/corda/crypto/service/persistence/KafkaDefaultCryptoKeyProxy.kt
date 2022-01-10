package net.corda.crypto.service.persistence

import net.corda.crypto.impl.closeGracefully
import net.corda.crypto.impl.config.CryptoPersistenceConfig
import net.corda.crypto.impl.config.DefaultConfigConsts
import net.corda.crypto.impl.persistence.SoftCryptoKeyRecord
import net.corda.data.crypto.persistence.DefaultCryptoKeyRecord
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.v5.base.util.contextLogger
import org.slf4j.Logger
import java.nio.ByteBuffer
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class KafkaDefaultCryptoKeyProxy(
    subscriptionFactory: SubscriptionFactory,
    publisherFactory: PublisherFactory,
    config: CryptoPersistenceConfig
) : CompactedProcessor<String, DefaultCryptoKeyRecord>, KafkaProxy<SoftCryptoKeyRecord> {
    companion object {
        private val logger: Logger = contextLogger()

        internal fun toKeyInfo(value: DefaultCryptoKeyRecord): SoftCryptoKeyRecord {
            val publicKey = value.publicKey?.array()
            return SoftCryptoKeyRecord(
                tenantId = value.memberId,
                alias = value.alias,
                publicKey = publicKey,
                privateKey = value.privateKey.array(),
                algorithmName = value.algorithmName,
                version = value.version
            )
        }

        internal fun toRecord(entity: SoftCryptoKeyRecord) =
            DefaultCryptoKeyRecord(
                entity.tenantId,
                entity.alias,
                if (entity.publicKey == null) {
                    null
                } else {
                    ByteBuffer.wrap(entity.publicKey)
                },
                ByteBuffer.wrap(entity.privateKey),
                entity.algorithmName,
                entity.version,
                Instant.now()
            )
    }

    private var keyMap = ConcurrentHashMap<String, SoftCryptoKeyRecord>()

    private val groupName: String = config.persistenceConfig.getString(
        DefaultConfigConsts.Kafka.GROUP_NAME_KEY,
        DefaultConfigConsts.Kafka.DefaultCryptoService.GROUP_NAME
    )

    private val topicName: String = config.persistenceConfig.getString(
        DefaultConfigConsts.Kafka.TOPIC_NAME_KEY,
        DefaultConfigConsts.Kafka.DefaultCryptoService.TOPIC_NAME
    )

    private val clientId: String = config.persistenceConfig.getString(
        DefaultConfigConsts.Kafka.CLIENT_ID_KEY,
        DefaultConfigConsts.Kafka.DefaultCryptoService.CLIENT_ID
    )

    private val pub: Publisher = publisherFactory.createPublisher(
        PublisherConfig(clientId)
    )

    private val sub: CompactedSubscription<String, DefaultCryptoKeyRecord> =
        subscriptionFactory.createCompactedSubscription(
            SubscriptionConfig(groupName, topicName),
            this
        ).also { it.start() }

    override val keyClass: Class<String> = String::class.java

    override val valueClass: Class<DefaultCryptoKeyRecord> = DefaultCryptoKeyRecord::class.java

    override fun onSnapshot(currentData: Map<String, DefaultCryptoKeyRecord>) {
        logger.debug("Processing snapshot of {} items", currentData.size)
        val map = ConcurrentHashMap<String, SoftCryptoKeyRecord>()
        for (record in currentData) {
            map[record.key] = toKeyInfo(record.value)
        }
        keyMap = map
    }

    override fun onNext(
        newRecord: Record<String, DefaultCryptoKeyRecord>,
        oldValue: DefaultCryptoKeyRecord?,
        currentData: Map<String, DefaultCryptoKeyRecord>
    ) {
        logger.debug("Processing new update")
        if (newRecord.value == null) {
            keyMap.remove(newRecord.key)
        } else {
            keyMap[newRecord.key] = toKeyInfo(newRecord.value!!)
        }
    }

    override fun publish(key: String, entity: SoftCryptoKeyRecord): CompletableFuture<Unit> {
        logger.debug("Publishing a record '{}' with key='{}'", valueClass.name, key)
        val record = toRecord(entity)
        return pub.publish(listOf(Record(topicName, key, record)))[0]
    }

    override fun getValue(tenantId: String, key: String): SoftCryptoKeyRecord? {
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
