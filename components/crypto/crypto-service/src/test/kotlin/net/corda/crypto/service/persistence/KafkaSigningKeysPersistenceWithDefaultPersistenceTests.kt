package net.corda.crypto.service.persistence

import net.corda.crypto.impl.persistence.KeyValuePersistence
import net.corda.crypto.impl.persistence.KeyValuePersistenceFactory
import net.corda.crypto.impl.persistence.SigningPersistentKeyInfo
import net.corda.data.crypto.persistence.SigningKeyRecord
import net.corda.v5.base.types.toHexString
import net.corda.v5.crypto.sha256Bytes
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.lessThanOrEqualTo
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

class KafkaSigningKeysPersistenceWithDefaultPersistenceTests {
    private lateinit var memberId: String
    private lateinit var memberId2: String
    private lateinit var kafka: KafkaInfrastructure
    private lateinit var factory: KeyValuePersistenceFactory
    private lateinit var signingPersistence: KeyValuePersistence<SigningPersistentKeyInfo, SigningPersistentKeyInfo>
    private lateinit var signingPersistence2: KeyValuePersistence<SigningPersistentKeyInfo, SigningPersistentKeyInfo>

    @BeforeEach
    fun setup() {
        memberId = UUID.randomUUID().toString()
        memberId2 = UUID.randomUUID().toString()
        kafka = KafkaInfrastructure()
        factory = kafka.createFactory(KafkaInfrastructure.defaultConfig)
        signingPersistence = factory.createSigningPersistence(
            memberId = memberId
        ) {
            it
        }
        signingPersistence2 = factory.createSigningPersistence(
            memberId = memberId2
        ) {
            it
        }
    }

    @AfterEach
    fun cleanup() {
        (factory as AutoCloseable).close()
        (signingPersistence as AutoCloseable).close()
    }

    private fun assertPublishedRecord(
        publishedRecord: Pair<String, SigningKeyRecord>,
        original: SigningPersistentKeyInfo
    ) {
        assertNotNull(publishedRecord.second)
        assertEquals(original.publicKeyHash, publishedRecord.first)
        assertEquals(original.alias, publishedRecord.second.alias)
        assertArrayEquals(original.publicKey, publishedRecord.second.publicKey.array())
        assertEquals(original.tenantId, publishedRecord.second.memberId)
        assertEquals(original.externalId, UUID.fromString(publishedRecord.second.externalId))
        assertEquals(original.masterKeyAlias, publishedRecord.second.masterKeyAlias)
        assertArrayEquals(original.privateKeyMaterial, publishedRecord.second.privateKeyMaterial.array())
        assertEquals(original.schemeCodeName, publishedRecord.second.schemeCodeName)
    }

    @Test
    @Timeout(5)
    fun `Should round trip persist and get signing cache value`() {
        val original = SigningPersistentKeyInfo(
            publicKeyHash = "hash1",
            alias = "alias1",
            publicKey = "Hello World!".toByteArray(),
            tenantId = memberId,
            externalId = UUID.randomUUID(),
            masterKeyAlias = "MK",
            privateKeyMaterial = "material".toByteArray(),
            schemeCodeName = "CODE"
        )
        signingPersistence.put("hash1", original)
        val records = kafka.getRecords<SigningKeyRecord>(
            KafkaInfrastructure.signingGroupName(KafkaInfrastructure.defaultConfig),
            KafkaInfrastructure.signingTopicName(KafkaInfrastructure.defaultConfig)
        )
        assertEquals(1, records.size)
        val publishedRecord = records[0]
        assertPublishedRecord(publishedRecord, original)
        val cachedRecord = signingPersistence.get("hash1")
        assertNotNull(cachedRecord)
        assertEquals(original.publicKeyHash, cachedRecord.publicKeyHash)
        assertEquals("alias1", cachedRecord.alias)
        assertArrayEquals(original.publicKey, cachedRecord.publicKey)
        assertEquals(original.tenantId, cachedRecord.tenantId)
        assertEquals(original.externalId, cachedRecord.externalId)
        assertEquals(original.masterKeyAlias, cachedRecord.masterKeyAlias)
        assertArrayEquals(original.privateKeyMaterial, cachedRecord.privateKeyMaterial)
        assertEquals(original.schemeCodeName, cachedRecord.schemeCodeName)
    }

    @Test
    @Timeout(5)
    fun `Should filter signing cache values based on member id`() {
        val original = SigningPersistentKeyInfo(
            publicKeyHash = "hash1",
            alias = "alias1",
            publicKey = "Hello World!".toByteArray(),
            tenantId = memberId,
            externalId = UUID.randomUUID(),
            masterKeyAlias = "MK",
            privateKeyMaterial = "material".toByteArray(),
            schemeCodeName = "CODE"
        )
        val original2 = SigningPersistentKeyInfo(
            publicKeyHash = "hash2",
            alias = "alias1",
            publicKey = "Hello World2!".toByteArray(),
            tenantId = memberId2,
            externalId = UUID.randomUUID(),
            masterKeyAlias = "MK",
            privateKeyMaterial = "material2".toByteArray(),
            schemeCodeName = "CODE2"
        )
        signingPersistence.put(original.publicKeyHash, original)
        signingPersistence2.put(original2.publicKeyHash, original2)
        val records = kafka.getRecords<SigningKeyRecord>(
            KafkaInfrastructure.signingGroupName(KafkaInfrastructure.defaultConfig),
            KafkaInfrastructure.signingTopicName(KafkaInfrastructure.defaultConfig),
            2
        )
        assertEquals(2, records.size)
        val publishedRecord = records.first { it.second.memberId == original.tenantId }
        val publishedRecord2 = records.first { it.second.memberId == original2.tenantId }
        assertPublishedRecord(publishedRecord, original)
        assertPublishedRecord(publishedRecord2, original2)
        val cachedRecord = signingPersistence.get(original.publicKeyHash)
        assertNotNull(cachedRecord)
        assertEquals(original.publicKeyHash, cachedRecord.publicKeyHash)
        assertEquals("alias1", cachedRecord.alias)
        assertArrayEquals(original.publicKey, cachedRecord.publicKey)
        assertEquals(original.tenantId, cachedRecord.tenantId)
        assertEquals(original.externalId, cachedRecord.externalId)
        assertEquals(original.masterKeyAlias, cachedRecord.masterKeyAlias)
        assertArrayEquals(original.privateKeyMaterial, cachedRecord.privateKeyMaterial)
        assertEquals(original.schemeCodeName, cachedRecord.schemeCodeName)
        val cachedRecord2 = signingPersistence2.get(original2.publicKeyHash)
        assertNotNull(cachedRecord2)
        assertEquals(original2.publicKeyHash, cachedRecord2.publicKeyHash)
        assertEquals("alias1", cachedRecord2.alias)
        assertArrayEquals(original2.publicKey, cachedRecord2.publicKey)
        assertEquals(original2.tenantId, cachedRecord2.tenantId)
        assertEquals(original2.externalId, cachedRecord2.externalId)
        assertEquals(original2.masterKeyAlias, cachedRecord2.masterKeyAlias)
        assertArrayEquals(original2.privateKeyMaterial, cachedRecord2.privateKeyMaterial)
        assertEquals(original2.schemeCodeName, cachedRecord2.schemeCodeName)
        assertNull(signingPersistence.get(original2.publicKeyHash))
        assertNull(signingPersistence2.get(original.publicKeyHash))
    }

    @Test
    @Timeout(5)
    fun `Should get signing cache record from subscription when it's not cached yet`() {
        val publicKey = "Hello World!".toByteArray()
        val original = SigningPersistentKeyInfo(
            publicKeyHash = publicKey.sha256Bytes().toHexString(),
            alias = "alias1",
            publicKey = publicKey,
            tenantId = memberId,
            externalId = UUID.randomUUID(),
            masterKeyAlias = "MK",
            privateKeyMaterial = "material".toByteArray(),
            schemeCodeName = "CODE"
        )
        kafka.publish(
            KafkaInfrastructure.signingClientId(KafkaInfrastructure.defaultConfig),
            signingPersistence,
            KafkaInfrastructure.signingTopicName(KafkaInfrastructure.defaultConfig),
            "hash1",
            KafkaSigningKeyProxy.toRecord(original)
        )
        val cachedRecord1 = signingPersistence.get("hash1")
        assertNotNull(cachedRecord1)
        assertEquals(original.publicKeyHash, cachedRecord1.publicKeyHash)
        assertEquals("alias1", cachedRecord1.alias)
        assertArrayEquals(original.publicKey, cachedRecord1.publicKey)
        assertEquals(original.tenantId, cachedRecord1.tenantId)
        assertEquals(original.externalId, cachedRecord1.externalId)
        assertEquals(original.masterKeyAlias, cachedRecord1.masterKeyAlias)
        assertArrayEquals(original.privateKeyMaterial, cachedRecord1.privateKeyMaterial)
        assertEquals(original.schemeCodeName, cachedRecord1.schemeCodeName)
        // again - will return from cache
        val cachedRecord2 = signingPersistence.get("hash1")
        assertNotNull(cachedRecord2)
        assertEquals(original.publicKeyHash, cachedRecord2.publicKeyHash)
        assertEquals("alias1", cachedRecord2.alias)
        assertArrayEquals(original.publicKey, cachedRecord2.publicKey)
        assertEquals(original.tenantId, cachedRecord2.tenantId)
        assertEquals(original.externalId, cachedRecord2.externalId)
        assertEquals(original.masterKeyAlias, cachedRecord2.masterKeyAlias)
        assertArrayEquals(original.privateKeyMaterial, cachedRecord2.privateKeyMaterial)
        assertEquals(original.schemeCodeName, cachedRecord2.schemeCodeName)
    }

    @Test
    @Timeout(5)
    fun `Should get signing cache null when it's not found`() {
        val cachedRecord = signingPersistence.get("hash1")
        assertNull(cachedRecord)
    }

    @Test
    @Timeout(5)
    fun `Should convert record containing null values to key info`() {
        val now = Instant.now()
        val record = SigningKeyRecord(
            memberId,
            "alias1",
            ByteBuffer.wrap("publicKey".toByteArray()),
            null,
            "masterKeyAlias",
            null,
            "CODE",
            2,
            now
        )
        val keyInfo = KafkaSigningKeyProxy.toKeyInfo(record)
        assertEquals(record.memberId, keyInfo.tenantId)
        assertEquals(record.alias, keyInfo.alias)
        assertArrayEquals(record.publicKey.array(), keyInfo.publicKey)
        assertNull(keyInfo.externalId)
        assertEquals(record.masterKeyAlias, keyInfo.masterKeyAlias)
        assertNull(keyInfo.privateKeyMaterial)
        assertEquals(record.schemeCodeName, keyInfo.schemeCodeName)
        assertEquals(record.version, keyInfo.version)
        assertEquals(keyInfo.publicKey.sha256Bytes().toHexString(), keyInfo.publicKeyHash)
    }

    @Test
    @Timeout(5)
    fun `Should convert key info containing null values to record`() {
        val publicKey = "publicKey".toByteArray()
        val now = Instant.now()
        val keyInfo = SigningPersistentKeyInfo(
            tenantId = memberId,
            alias = "alias1",
            publicKey = publicKey,
            externalId = null,
            masterKeyAlias =  "masterKeyAlias",
            privateKeyMaterial =  null,
            schemeCodeName =  "CODE",
            version =  2,
            publicKeyHash = publicKey.sha256Bytes().toHexString()
        )
        val record = KafkaSigningKeyProxy.toRecord(keyInfo)
        assertEquals(keyInfo.tenantId, record.memberId)
        assertEquals(keyInfo.alias, record.alias)
        assertArrayEquals(keyInfo.publicKey, record.publicKey.array())
        assertNull(keyInfo.externalId)
        assertEquals(keyInfo.masterKeyAlias, record.masterKeyAlias)
        assertNull(keyInfo.privateKeyMaterial)
        assertEquals(keyInfo.schemeCodeName, record.schemeCodeName)
        assertEquals(keyInfo.version, record.version)
        assertThat(
            record.timestamp.toEpochMilli(),
            allOf(greaterThanOrEqualTo(now.toEpochMilli()), lessThanOrEqualTo(now.toEpochMilli() + 5000))
        )
    }
}