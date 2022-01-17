package net.corda.crypto.persistence.kafka

import net.corda.crypto.component.persistence.EntityKeyInfo
import net.corda.crypto.component.persistence.KeyValuePersistence
import net.corda.crypto.component.persistence.SoftKeysRecordInfo
import net.corda.data.crypto.persistence.SoftKeysRecord
import net.corda.schema.Schemas
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

class KafkaDefaultCryptoKeysPersistenceTests {
    private lateinit var tenantId: String
    private lateinit var tenantId2: String
    private lateinit var kafka: KafkaInfrastructure
    private lateinit var provider: KafkaSoftPersistenceProvider
    private lateinit var persistence: KeyValuePersistence<SoftKeysRecordInfo, SoftKeysRecord>
    private lateinit var persistence2: KeyValuePersistence<SoftKeysRecordInfo, SoftKeysRecord>

    @BeforeEach
    fun setup() {
        tenantId = UUID.randomUUID().toString()
        tenantId2 = UUID.randomUUID().toString()
        kafka = KafkaInfrastructure()
        provider = kafka.createSoftPersistenceProvider()
        persistence = provider.getInstance(tenantId) {
            SoftKeysRecordInfo(tenantId = it.tenantId)
        }
        persistence2 = provider.getInstance(tenantId2) {
            SoftKeysRecordInfo(tenantId = it.tenantId)
        }
    }

    @AfterEach
    fun cleanup() {
        (provider as AutoCloseable).close()
        (persistence as AutoCloseable).close()
        (persistence2 as AutoCloseable).close()
    }

    private fun assertPublishedRecord(
        actual: SoftKeysRecord,
        original: SoftKeysRecord
    ) {
        assertEquals(original.alias, actual.alias)
        assertEquals(original.tenantId, actual.tenantId)
        assertArrayEquals(original.publicKey.array(), actual.publicKey.array())
        assertArrayEquals(original.privateKey.array(), actual.privateKey.array())
        assertEquals(original.algorithmName, actual.algorithmName)
        assertEquals(original.version, actual.version)
    }

    @Test
    @Timeout(5)
    fun `Should round trip persist and get default crypto cache value`() {
        // alias is prefixed by the member id when it's used by the DefaultCryptoKeyCacheImpl
        val alias1 = UUID.randomUUID().toString()
        val original = SoftKeysRecord(
            tenantId,
            alias1,
            ByteBuffer.wrap("Public Key!".toByteArray()),
            ByteBuffer.wrap("Private Key!".toByteArray()),
            "algo",
            1,
            Instant.now()
        )
        persistence.put(original, EntityKeyInfo(EntityKeyInfo.ALIAS, original.alias))
        val records = kafka.getRecords<SoftKeysRecord>(
            KafkaSoftPersistenceProcessor.GROUP_NAME,
            Schemas.Crypto.SOFT_HSM_PERSISTENCE_TOPIC
        )
        assertEquals(1, records.size)
        val publishedRecord = records[0]
        assertEquals(publishedRecord.first, original.alias)
        assertPublishedRecord(publishedRecord.second, original)
        val cachedRecord = persistence.get(original.alias)
        assertNotNull(cachedRecord)
        assertEquals(original.tenantId, cachedRecord.tenantId)
    }

    /*
    @Test
    @Timeout(5)
    fun `Should filter default crypto cache values based on member id`() {
        // alias is prefixed by the member id when it's used by the DefaultCryptoKeyCacheImpl
        val original = SoftKeysRecord(
            alias = "$tenantId:alias1",
            publicKey = "Public Key!".toByteArray(),
            tenantId = tenantId,
            privateKey = "Private Key!".toByteArray(),
            algorithmName = "algo",
            version = 2
        )
        val original2 = SoftKeysRecord(
            alias = "$tenantId2:alias1",
            publicKey = "Public Key2!".toByteArray(),
            tenantId = tenantId2,
            privateKey = "Private Key2!".toByteArray(),
            algorithmName = "algo",
            version = 2
        )
        persistence.put(original.alias, original)
        persistence2.put(original2.alias, original2)
        val records = kafka.getRecords<SoftKeysRecord>(
            KafkaInfrastructure.cryptoSvcGroupName(KafkaInfrastructure.customConfig),
            KafkaInfrastructure.cryptoSvcTopicName(KafkaInfrastructure.customConfig),
            2
        )
        assertEquals(2, records.size)
        val publishedRecord = records.first { it.second.alias == original.alias }
        val publishedRecord2 = records.first { it.second.alias == original2.alias }
        assertPublishedRecord(publishedRecord, original)
        assertPublishedRecord(publishedRecord2, original2)
        val cachedRecord = persistence.get(original.alias)
        assertNotNull(cachedRecord)
        assertEquals(original.tenantId, cachedRecord.tenantId)
        val cachedRecord2 = persistence2.get(original2.alias)
        assertNotNull(cachedRecord2)
        assertEquals(original2.tenantId, cachedRecord2.tenantId)
        assertNull(persistence.get(original2.alias))
        assertNull(persistence2.get(original.alias))
    }

    @Test
    @Timeout(5)
    fun `Should get default crypto cache record from subscription when it's not cached yet`() {
        // alias is prefixed by the member id when it's used by the DefaultCryptoKeyCacheImpl
        val original = SoftKeysRecord(
            alias = "$tenantId:alias1",
            publicKey = "Public Key!".toByteArray(),
            tenantId = tenantId,
            privateKey = "Private Key!".toByteArray(),
            algorithmName = "algo",
            version = 2
        )
        kafka.publish(
            KafkaInfrastructure.cryptoSvcClientId(KafkaInfrastructure.customConfig),
            persistence,
            KafkaInfrastructure.cryptoSvcTopicName(KafkaInfrastructure.customConfig),
            original.alias,
            KafkaDefaultCryptoKeyProxy.toRecord(original)
        )
        val cachedRecord1 = persistence.get(original.alias)
        assertNotNull(cachedRecord1)
        assertEquals(original.tenantId, cachedRecord1.tenantId)
        // again - will return from cache
        val cachedRecord2 = persistence.get(original.alias)
        assertNotNull(cachedRecord2)
        assertEquals(original.tenantId, cachedRecord2.tenantId)
    }

    @Test
    @Timeout(5)
    fun `Should get default crypto cache null when it's not found`() {
        val cachedRecord = persistence.get("$tenantId:alias1")
        assertNull(cachedRecord)
    }

    @Test
    @Timeout(5)
    fun `Should convert record containing null values to key info`() {
        val now = Instant.now()
        val record = SoftKeysRecord(
            tenantId,
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
        val keyInfo = SoftKeysRecord(
            tenantId = tenantId,
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
    
     */
}