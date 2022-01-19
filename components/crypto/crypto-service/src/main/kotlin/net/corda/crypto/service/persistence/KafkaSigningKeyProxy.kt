package net.corda.crypto.service.persistence

import net.corda.crypto.impl.closeGracefully
import net.corda.crypto.impl.config.CryptoPersistenceConfig
import net.corda.crypto.impl.config.DefaultConfigConsts
import net.corda.crypto.impl.persistence.SigningPersistentKeyInfo
import net.corda.data.crypto.persistence.SigningKeyRecord
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.v5.base.types.toHexString
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.sha256Bytes
import org.slf4j.Logger
import java.nio.ByteBuffer
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class KafkaSigningKeyProxy(
    subscriptionFactory: SubscriptionFactory,
    publisherFactory: PublisherFactory,
    config: CryptoPersistenceConfig
) : CompactedProcessor<String, SigningKeyRecord>, KafkaProxy<SigningPersistentKeyInfo> {
    companion object {
        private val logger: Logger = contextLogger()

        internal fun toKeyInfo(value: SigningKeyRecord): SigningPersistentKeyInfo {
            val publicKey = value.publicKey.array()
            return SigningPersistentKeyInfo(
                memberId = value.memberId,
                publicKeyHash = publicKey.sha256Bytes().toHexString(),
                alias = value.alias,
                publicKey = publicKey,
                externalId = if (value.externalId.isNullOrBlank()) {
                    null
                } else {
                    UUID.fromString(value.externalId)
                },
                masterKeyAlias = value.masterKeyAlias,
                privateKeyMaterial = value.privateKeyMaterial?.array(),
                schemeCodeName = value.schemeCodeName,
                version = value.version
            )
        }

        internal fun toRecord(entity: SigningPersistentKeyInfo) = SigningKeyRecord(
            entity.memberId,
            entity.alias,
            ByteBuffer.wrap(entity.publicKey),
            entity.externalId?.toString(),
            entity.masterKeyAlias,
            if (entity.privateKeyMaterial == null) {
                null
            } else {
                ByteBuffer.wrap(entity.privateKeyMaterial)
            },
            entity.schemeCodeName,
            entity.version,
            Instant.now()
        )
    }

    private var keyMap = ConcurrentHashMap<String, SigningPersistentKeyInfo>()

    private val groupName: String = config.persistenceConfig.getString(
        DefaultConfigConsts.Kafka.GROUP_NAME_KEY,
        DefaultConfigConsts.Kafka.Signing.GROUP_NAME
    )

    private val topicName: String = config.persistenceConfig.getString(
        DefaultConfigConsts.Kafka.TOPIC_NAME_KEY,
        DefaultConfigConsts.Kafka.Signing.TOPIC_NAME
    )

    private val clientId: String = config.persistenceConfig.getString(
        DefaultConfigConsts.Kafka.CLIENT_ID_KEY,
        DefaultConfigConsts.Kafka.Signing.CLIENT_ID
    )

    private val pub: Publisher = publisherFactory.createPublisher(
        PublisherConfig(clientId)
    )

    private val sub: CompactedSubscription<String, SigningKeyRecord> =
        subscriptionFactory.createCompactedSubscription(
            SubscriptionConfig(groupName, topicName),
            this
        ).also { it.start() }

    override val keyClass: Class<String> = String::class.java

    override val valueClass: Class<SigningKeyRecord> = SigningKeyRecord::class.java

    override fun onSnapshot(currentData: Map<String, SigningKeyRecord>) {
        logger.debug("Processing snapshot of {} items", currentData.size)
        val map = ConcurrentHashMap<String, SigningPersistentKeyInfo>()
        for (record in currentData) {
            map[record.key] = toKeyInfo(record.value)
        }
        keyMap = map
    }

    override fun onNext(
        newRecord: Record<String, SigningKeyRecord>,
        oldValue: SigningKeyRecord?,
        currentData: Map<String, SigningKeyRecord>
    ) {
        logger.debug("Processing new update")
        if(newRecord.value == null) {
            keyMap.remove(newRecord.key)
        } else {
            keyMap[newRecord.key] = toKeyInfo(newRecord.value!!)
        }
    }

    override fun publish(key: String, entity: SigningPersistentKeyInfo): CompletableFuture<Unit> {
        logger.debug("Publishing a record '{}' with key='{}'", valueClass.name, key)
        val record = toRecord(entity)
        return pub.publish(listOf(Record(topicName, key, record)))[0]
    }

    override fun getValue(memberId: String, key: String): SigningPersistentKeyInfo? {
        logger.debug("Requesting a record '{}' with key='{}' for member='{}'", valueClass.name, key, memberId)
        val value = keyMap[key]
        return if (value == null || value.memberId != memberId) {
            if (value != null) {
                logger.warn(
                    "The requested record '{}' with key='{}' for member='{}' is actually for '{}' member",
                    valueClass.name,
                    key,
                    memberId,
                    value.memberId
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
