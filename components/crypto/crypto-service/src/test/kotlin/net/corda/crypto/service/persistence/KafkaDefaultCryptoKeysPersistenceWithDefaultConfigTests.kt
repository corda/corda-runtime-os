package net.corda.crypto.service.persistence

import net.corda.crypto.impl.persistence.SoftCryptoKeyRecordInfo
import net.corda.crypto.impl.persistence.SoftCryptoKeyRecord
import net.corda.crypto.impl.persistence.KeyValuePersistence
import net.corda.crypto.impl.persistence.KeyValuePersistenceFactory
import net.corda.data.crypto.persistence.DefaultCryptoKeyRecord
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class KafkaDefaultCryptoKeysPersistenceWithDefaultConfigTests {
    private lateinit var memberId: String
    private lateinit var memberId2: String
    private lateinit var kafka: KafkaInfrastructure
    private lateinit var factory: KeyValuePersistenceFactory
    private lateinit var defaultPersistence: KeyValuePersistence<SoftCryptoKeyRecordInfo, SoftCryptoKeyRecord>
    private lateinit var defaultPersistence2: KeyValuePersistence<SoftCryptoKeyRecordInfo, SoftCryptoKeyRecord>

    @BeforeEach
    fun setup() {
        memberId = UUID.randomUUID().toString()
        memberId2 = UUID.randomUUID().toString()
        kafka = KafkaInfrastructure()
        factory = kafka.createFactory(KafkaInfrastructure.defaultConfig)
        defaultPersistence = factory.createDefaultCryptoPersistence(
            tenantId = memberId
        ) {
            SoftCryptoKeyRecordInfo(tenantId = it.tenantId)
        }
        defaultPersistence2 = factory.createDefaultCryptoPersistence(
            tenantId = memberId2
        ) {
            SoftCryptoKeyRecordInfo(tenantId = it.tenantId)
        }
    }

    @AfterEach
    fun cleanup() {
        (factory as AutoCloseable).close()
        (defaultPersistence as AutoCloseable).close()
    }

    private fun assertPublishedRecord(
        publishedRecord: Pair<String, DefaultCryptoKeyRecord>,
        original: SoftCryptoKeyRecord
    ) {
        assertNotNull(publishedRecord.second)
        assertEquals(original.alias, publishedRecord.first)
        assertEquals(original.alias, publishedRecord.second.alias)
        assertEquals(original.tenantId, publishedRecord.second.memberId)
        assertArrayEquals(original.publicKey, publishedRecord.second.publicKey.array())
        assertArrayEquals(original.privateKey, publishedRecord.second.privateKey.array())
        assertEquals(original.algorithmName, publishedRecord.second.algorithmName)
        assertEquals(original.version, publishedRecord.second.version)
    }

    @Test
    @Timeout(5)
    fun `Should round trip persist and get default crypto cache value`() {
        // alias is prefixed by the member id when it's used by the DefaultCryptoKeyCacheImpl
        val original = SoftCryptoKeyRecord(
            alias = "$memberId:alias1",
            publicKey = "Public Key!".toByteArray(),
            tenantId = memberId,
            privateKey = "Private Key!".toByteArray(),
            algorithmName = "algo",
            version = 2
        )
        defaultPersistence.put(original.alias, original)
        val records = kafka.getRecords<DefaultCryptoKeyRecord>(
            KafkaInfrastructure.cryptoSvcGroupName(KafkaInfrastructure.defaultConfig),
            KafkaInfrastructure.cryptoSvcTopicName(KafkaInfrastructure.defaultConfig)
        )
        assertEquals(1, records.size)
        val publishedRecord = records[0]
        assertPublishedRecord(publishedRecord, original)
        val cachedRecord = defaultPersistence.get(original.alias)
        assertNotNull(cachedRecord)
        assertEquals(original.tenantId, cachedRecord.tenantId)
    }

    @Test
    @Timeout(5)
    fun `Should filter default crypto cache values based on member id`() {
        // alias is prefixed by the member id when it's used by the DefaultCryptoKeyCacheImpl
        val original = SoftCryptoKeyRecord(
            alias = "$memberId:alias1",
            publicKey = "Public Key!".toByteArray(),
            tenantId = memberId,
            privateKey = "Private Key!".toByteArray(),
            algorithmName = "algo",
            version = 2
        )
        val original2 = SoftCryptoKeyRecord(
            alias = "$memberId2:alias1",
            publicKey = "Public Key2!".toByteArray(),
            tenantId = memberId2,
            privateKey = "Private Key2!".toByteArray(),
            algorithmName = "algo",
            version = 2
        )
        defaultPersistence.put(original.alias, original)
        defaultPersistence2.put(original2.alias, original2)
        val records = kafka.getRecords<DefaultCryptoKeyRecord>(
            KafkaInfrastructure.cryptoSvcGroupName(KafkaInfrastructure.defaultConfig),
            KafkaInfrastructure.cryptoSvcTopicName(KafkaInfrastructure.defaultConfig),
            2)
        assertEquals(2, records.size)
        val publishedRecord = records.first { it.second.alias == original.alias }
        val publishedRecord2 = records.first { it.second.alias == original2.alias }
        assertPublishedRecord(publishedRecord, original)
        assertPublishedRecord(publishedRecord2, original2)
        val cachedRecord = defaultPersistence.get(original.alias)
        assertNotNull(cachedRecord)
        assertEquals(original.tenantId, cachedRecord.tenantId)
        val cachedRecord2 = defaultPersistence2.get(original2.alias)
        assertNotNull(cachedRecord2)
        assertEquals(original2.tenantId, cachedRecord2.tenantId)
        assertNull(defaultPersistence.get(original2.alias))
        assertNull(defaultPersistence2.get(original.alias))
    }

    @Test
    @Timeout(5)
    fun `Should get default crypto cache record from subscription when it's not cached yet`() {
        // alias is prefixed by the member id when it's used by the DefaultCryptoKeyCacheImpl
        val original = SoftCryptoKeyRecord(
            alias = "$memberId:alias1",
            publicKey = "Public Key!".toByteArray(),
            tenantId = memberId,
            privateKey = "Private Key!".toByteArray(),
            algorithmName = "algo",
            version = 2
        )
        kafka.publish(
            KafkaInfrastructure.cryptoSvcClientId(KafkaInfrastructure.defaultConfig),
            defaultPersistence,
            KafkaInfrastructure.cryptoSvcTopicName(KafkaInfrastructure.defaultConfig),
            original.alias,
            KafkaDefaultCryptoKeyProxy.toRecord(original)
        )
        val cachedRecord1 = defaultPersistence.get(original.alias)
        assertNotNull(cachedRecord1)
        assertEquals(original.tenantId, cachedRecord1.tenantId)
        // again - will return from cache
        val cachedRecord2 = defaultPersistence.get(original.alias)
        assertNotNull(cachedRecord2)
        assertEquals(original.tenantId, cachedRecord2.tenantId)
    }

    @Test
    @Timeout(5)
    fun `Should get default crypto cache null when it's not found`() {
        val cachedRecord = defaultPersistence.get("$memberId:alias1")
        assertNull(cachedRecord)
    }

    @Test
    @Timeout(5)
    fun `Should convert record containing null values to key info`() {
        val now = Instant.now()
        val record = DefaultCryptoKeyRecord(
            memberId,
            "alias1",
            null,
            ByteBuffer.wrap("publicKey".toByteArray()),
            "ALGO",
            3,
            now
        )
        val keyInfo = KafkaDefaultCryptoKeyProxy.toKeyInfo(record)
        assertEquals(record.memberId, keyInfo.tenantId)
        assertEquals(record.alias, keyInfo.alias)
        assertNull(keyInfo.publicKey)
        assertArrayEquals(record.privateKey.array(), keyInfo.privateKey)
        assertEquals(record.algorithmName, keyInfo.algorithmName)
        assertEquals(record.version, keyInfo.version)
    }

    @Test
    @Timeout(5)
    fun `Should convert key info containing null values to record`() {
        val now = Instant.now()
        val keyInfo = SoftCryptoKeyRecord(
            tenantId = memberId,
            alias = "alias1",
            publicKey = null,
            privateKey = "privateKey".toByteArray(),
            algorithmName = "ALGO",
            version =  2
        )
        val record = KafkaDefaultCryptoKeyProxy.toRecord(keyInfo)
        assertEquals(keyInfo.tenantId, record.memberId)
        assertEquals(keyInfo.alias, record.alias)
        assertNull(keyInfo.publicKey)
        assertArrayEquals(keyInfo.privateKey, record.privateKey.array())
        assertEquals(keyInfo.algorithmName, record.algorithmName)
        assertEquals(keyInfo.version, record.version)
        MatcherAssert.assertThat(
            record.timestamp.toEpochMilli(),
            Matchers.allOf(
                Matchers.greaterThanOrEqualTo(now.toEpochMilli()),
                Matchers.lessThanOrEqualTo(now.toEpochMilli() + 5000)
            )
        )
    }
}