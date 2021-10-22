package net.corda.crypto.service.persistence

import net.corda.crypto.impl.persistence.DefaultCryptoCachedKeyInfo
import net.corda.crypto.impl.persistence.DefaultCryptoPersistentKeyInfo
import net.corda.crypto.impl.persistence.KeyValuePersistence
import net.corda.crypto.impl.persistence.KeyValuePersistenceFactory
import net.corda.crypto.service.persistence.KafkaInfrastructure.Companion.KEY_CACHE_TOPIC_NAME
import net.corda.crypto.service.persistence.KafkaInfrastructure.Companion.wait
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class KafkaDefaultCryptoKeysPersistenceSnapshotTests {
    private lateinit var memberId: String
    private lateinit var kafka: KafkaInfrastructure
    private lateinit var factory: KeyValuePersistenceFactory
    private lateinit var defaultPersistence:
            KeyValuePersistence<DefaultCryptoCachedKeyInfo, DefaultCryptoPersistentKeyInfo>
    private lateinit var original1: DefaultCryptoPersistentKeyInfo
    private lateinit var original2: DefaultCryptoPersistentKeyInfo

    @BeforeEach
    fun setup() {
        memberId = UUID.randomUUID().toString()
        kafka = KafkaInfrastructure()
        original1 = DefaultCryptoPersistentKeyInfo(
            alias = "$memberId:alias1",
            publicKey = "Public Key1".toByteArray(),
            memberId = memberId,
            privateKey = "Private Key1".toByteArray(),
            algorithmName = "algo",
            version = 2
        )
        original2 = DefaultCryptoPersistentKeyInfo(
            alias = "$memberId:alias12",
            publicKey = "Public Key2".toByteArray(),
            memberId = memberId,
            privateKey = "Private Key2".toByteArray(),
            algorithmName = "algo",
            version = 2
        )
        factory = kafka.createFactory {
            kafka.publish<DefaultCryptoCachedKeyInfo, DefaultCryptoPersistentKeyInfo>(
                null,
                KEY_CACHE_TOPIC_NAME,
                original1.alias,
                KafkaDefaultCryptoKeyProxy.toRecord(original1)
            )
            kafka.publish<DefaultCryptoCachedKeyInfo, DefaultCryptoPersistentKeyInfo>(
                null,
                KEY_CACHE_TOPIC_NAME,
                original2.alias,
                KafkaDefaultCryptoKeyProxy.toRecord(original2)
            )
        }
        defaultPersistence = factory.createDefaultCryptoPersistence(
            memberId = memberId
        ) {
            DefaultCryptoCachedKeyInfo(memberId = it.memberId)
        }
    }

    @AfterEach
    fun cleanup() {
        (factory as AutoCloseable).close()
        (defaultPersistence as AutoCloseable).close()
    }

    @Test
    @Timeout(5)
    fun `Should load snapshot and get default crypto cache value`() {
        val cachedRecord1 = defaultPersistence.wait(original1.alias)
        assertNotNull(cachedRecord1)
        assertEquals(original1.memberId, cachedRecord1.memberId)
        val cachedRecord2 = defaultPersistence.get(original2.alias)
        assertNotNull(cachedRecord2)
        assertEquals(original1.memberId, cachedRecord2.memberId)
        val cachedRecord3 = defaultPersistence.get(UUID.randomUUID().toString())
        assertNull(cachedRecord3)
    }
}