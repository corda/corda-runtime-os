package net.corda.crypto.persistence.kafka

import net.corda.crypto.CryptoConsts
import net.corda.crypto.component.persistence.EntityKeyInfo
import net.corda.crypto.component.persistence.KeyValuePersistence
import net.corda.data.crypto.persistence.SigningKeysRecord
import net.corda.schema.Schemas
import net.corda.v5.base.types.toHexString
import net.corda.v5.crypto.sha256Bytes
import org.bouncycastle.util.encoders.Base32
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

class KafkaSigningKeysPersistenceTests {
    private lateinit var tenantId: String
    private lateinit var tenantId2: String
    private lateinit var alias1: String
    private lateinit var kafka: KafkaInfrastructure
    private lateinit var provider: KafkaSigningKeysPersistenceProvider
    private lateinit var signingPersistence: KeyValuePersistence<SigningKeysRecord, SigningKeysRecord>
    private lateinit var signingPersistence2: KeyValuePersistence<SigningKeysRecord, SigningKeysRecord>

    @BeforeEach
    fun setup() {
        tenantId = UUID.randomUUID().toString()
        tenantId2 = UUID.randomUUID().toString()
        alias1 = UUID.randomUUID().toString()
        kafka = KafkaInfrastructure()
        provider = kafka.createSigningKeysPersistenceProvider()
        signingPersistence = provider.getInstance(tenantId) { it }
        signingPersistence2 = provider.getInstance(tenantId2) { it }
    }

    @AfterEach
    fun cleanup() {
        (provider as AutoCloseable).close()
        (signingPersistence as AutoCloseable).close()
        (signingPersistence2 as AutoCloseable).close()
    }

    private fun assertPublishedRecord(
        publishedRecord: SigningKeysRecord,
        original: SigningKeysRecord
    ) {
        assertNotNull(publishedRecord)
        assertEquals(original.tenantId, publishedRecord.tenantId)
        assertEquals(original.category, publishedRecord.category)
        assertEquals(original.alias, publishedRecord.alias)
        assertEquals(original.hsmAlias, publishedRecord.hsmAlias)
        assertArrayEquals(original.publicKey.array(), publishedRecord.publicKey.array())
        assertEquals(original.externalId, publishedRecord.externalId)
        assertEquals(original.masterKeyAlias, publishedRecord.masterKeyAlias)
        assertArrayEquals(original.privateKeyMaterial.array(), publishedRecord.privateKeyMaterial.array())
        assertEquals(original.schemeCodeName, publishedRecord.schemeCodeName)
    }

    @Test
    @Timeout(5)
    fun `Should round trip persist and get signing cache value`() {
        val original = SigningKeysRecord(
            tenantId,
            CryptoConsts.CryptoCategories.LEDGER,
            alias1,
            Base32.toBase32String((tenantId + alias1).encodeToByteArray().sha256Bytes()).take(30).toLowerCase(),
            ByteBuffer.wrap("Hello World!".toByteArray()),
            ByteBuffer.wrap("material".toByteArray()),
            "CODE",
            "MK",
            UUID.randomUUID().toString(),
            1,
            Instant.now()
        )
        signingPersistence.put(
            original,
            EntityKeyInfo(EntityKeyInfo.PUBLIC_KEY, "by-hash1"),
            EntityKeyInfo(EntityKeyInfo.ALIAS, "by-alias1")
        )
        val records = kafka.getRecords<SigningKeysRecord>(
            Schemas.Crypto.SIGNING_KEY_PERSISTENCE_TOPIC,
            Schemas.Crypto.SIGNING_KEY_PERSISTENCE_TOPIC
        )
        assertEquals(2, records.size)
        val publishedRecord1 = records[0]
        assertEquals("by-hash1", publishedRecord1.first)
        assertPublishedRecord(publishedRecord1.second, original)
        val publishedRecord2 = records[1]
        assertEquals("by-alias1", publishedRecord2.first)
        assertPublishedRecord(publishedRecord2.second, original)
        val cachedRecord1 = signingPersistence.get("by-hash1")
        assertNotNull(cachedRecord1)
        assertPublishedRecord(cachedRecord1, original)
        val cachedRecord2 = signingPersistence.get("by-alias1")
        assertNotNull(cachedRecord2)
        assertPublishedRecord(cachedRecord2, original)
    }

    /*
    @Test
    @Timeout(5)
    fun `Should filter signing cache values based on member id`() {
        val original = SigningKeysRecord(
            keyDerivedId = "hash1",
            alias = "alias1",
            publicKey = "Hello World!".toByteArray(),
            tenantId = tenantId,
            externalId = UUID.randomUUID(),
            masterKeyAlias = "MK",
            privateKeyMaterial = "material".toByteArray(),
            schemeCodeName = "CODE"
        )
        val original2 = SigningKeysRecord(
            keyDerivedId = "hash2",
            alias = "alias1",
            publicKey = "Hello World2!".toByteArray(),
            tenantId = tenantId2,
            externalId = UUID.randomUUID(),
            masterKeyAlias = "MK",
            privateKeyMaterial = "material2".toByteArray(),
            schemeCodeName = "CODE2"
        )
        signingPersistence.put(original.keyDerivedId, original)
        signingPersistence2.put(original2.keyDerivedId, original2)
        val records = kafka.getRecords<SigningKeysRecord>(
            KafkaInfrastructure.cryptoSvcGroupName(KafkaInfrastructure.customConfig),
            Schemas.Crypto.SIGNING_KEY_PERSISTENCE_TOPIC,
            2
        )
        assertEquals(2, records.size)
        val publishedRecord = records.first { it.second.memberId == original.tenantId }
        val publishedRecord2 = records.first { it.second.memberId == original2.tenantId }
        assertPublishedRecord(publishedRecord, original)
        assertPublishedRecord(publishedRecord2, original2)
        val cachedRecord = signingPersistence.get(original.keyDerivedId)
        assertNotNull(cachedRecord)
        assertEquals(original.keyDerivedId, cachedRecord.keyDerivedId)
        assertEquals("alias1", cachedRecord.alias)
        assertArrayEquals(original.publicKey, cachedRecord.publicKey)
        assertEquals(original.tenantId, cachedRecord.tenantId)
        assertEquals(original.externalId, cachedRecord.externalId)
        assertEquals(original.masterKeyAlias, cachedRecord.masterKeyAlias)
        assertArrayEquals(original.privateKeyMaterial, cachedRecord.privateKeyMaterial)
        assertEquals(original.schemeCodeName, cachedRecord.schemeCodeName)
        val cachedRecord2 = signingPersistence2.get(original2.keyDerivedId)
        assertNotNull(cachedRecord2)
        assertEquals(original2.keyDerivedId, cachedRecord2.keyDerivedId)
        assertEquals("alias1", cachedRecord2.alias)
        assertArrayEquals(original2.publicKey, cachedRecord2.publicKey)
        assertEquals(original2.tenantId, cachedRecord2.tenantId)
        assertEquals(original2.externalId, cachedRecord2.externalId)
        assertEquals(original2.masterKeyAlias, cachedRecord2.masterKeyAlias)
        assertArrayEquals(original2.privateKeyMaterial, cachedRecord2.privateKeyMaterial)
        assertEquals(original2.schemeCodeName, cachedRecord2.schemeCodeName)
        assertNull(signingPersistence.get(original2.keyDerivedId))
        assertNull(signingPersistence2.get(original.keyDerivedId))
    }

    @Test
    @Timeout(5)
    fun `Should get signing cache record from subscription when it's not cached yet`() {
        val publicKey = "Hello World!".toByteArray()
        val original = SigningKeysRecord(
            keyDerivedId = publicKey.sha256Bytes().toHexString(),
            alias = "alias1",
            publicKey = publicKey,
            tenantId = tenantId,
            externalId = UUID.randomUUID(),
            masterKeyAlias = "MK",
            privateKeyMaterial = "material".toByteArray(),
            schemeCodeName = "CODE"
        )
        kafka.publish(
            KafkaInfrastructure.signingClientId(KafkaInfrastructure.customConfig),
            signingPersistence,
            Schemas.Crypto.SIGNING_KEY_PERSISTENCE_TOPIC,
            "hash1",
            original
        )
        val cachedRecord1 = signingPersistence.get("hash1")
        assertNotNull(cachedRecord1)
        assertEquals(original.keyDerivedId, cachedRecord1.keyDerivedId)
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
        assertEquals(original.keyDerivedId, cachedRecord2.keyDerivedId)
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
        val record = SigningKeysRecord(
            tenantId,
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
        assertEquals(keyInfo.publicKey.sha256Bytes().toHexString(), keyInfo.keyDerivedId)
    }

    @Test
    @Timeout(5)
    fun `Should convert key info containing null values to record`() {
        val publicKey = "publicKey".toByteArray()
        val now = Instant.now()
        val keyInfo = SigningKeysRecord(
            tenantId = tenantId,
            alias = "alias1",
            publicKey = publicKey,
            externalId = null,
            masterKeyAlias =  "masterKeyAlias",
            privateKeyMaterial =  null,
            schemeCodeName =  "CODE",
            version =  2,
            keyDerivedId = publicKey.sha256Bytes().toHexString()
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

     */
}