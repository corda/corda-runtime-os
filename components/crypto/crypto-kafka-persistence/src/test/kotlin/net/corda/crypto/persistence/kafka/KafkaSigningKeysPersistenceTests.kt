package net.corda.crypto.persistence.kafka

import net.corda.crypto.CryptoConsts
import net.corda.crypto.component.persistence.EntityKeyInfo
import net.corda.crypto.component.persistence.KeyValuePersistence
import net.corda.data.crypto.persistence.SigningKeysRecord
import net.corda.schema.Schemas
import net.corda.v5.crypto.sha256Bytes
import org.bouncycastle.util.encoders.Base32
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
import kotlin.test.assertSame

class KafkaSigningKeysPersistenceTests {
    private lateinit var tenantId: String
    private lateinit var alias1: String
    private lateinit var kafka: KafkaInfrastructure
    private lateinit var provider: KafkaSigningKeysPersistenceProvider
    private lateinit var signingPersistence: KeyValuePersistence<SigningKeysRecord, SigningKeysRecord>

    @BeforeEach
    fun setup() {
        tenantId = UUID.randomUUID().toString()
        alias1 = UUID.randomUUID().toString()
        kafka = KafkaInfrastructure()
        provider = kafka.createSigningKeysPersistenceProvider()
        signingPersistence = provider.getInstance(tenantId) { it }
    }

    @AfterEach
    fun cleanup() {
        (provider as AutoCloseable).close()
        (signingPersistence as AutoCloseable).close()
    }

    private fun assertRecord(
        actual: SigningKeysRecord,
        original: SigningKeysRecord
    ) {
        assertNotNull(actual)
        assertEquals(original.tenantId, actual.tenantId)
        assertEquals(original.category, actual.category)
        assertEquals(original.alias, actual.alias)
        assertEquals(original.hsmAlias, actual.hsmAlias)
        assertArrayEquals(original.publicKey.array(), actual.publicKey.array())
        assertEquals(original.externalId, actual.externalId)
        assertEquals(original.masterKeyAlias, actual.masterKeyAlias)
        assertArrayEquals(original.privateKeyMaterial.array(), actual.privateKeyMaterial.array())
        assertEquals(original.schemeCodeName, actual.schemeCodeName)
    }

    @Test
    @Timeout(5)
    fun `Should get signing cache null when it's not found`() {
        val cachedRecord = signingPersistence.get(UUID.randomUUID().toString())
        assertNull(cachedRecord)
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
        val publishedRecord1 = records.firstOrNull { it.first == "by-hash1"}
        assertNotNull(publishedRecord1)
        assertRecord(publishedRecord1.second, original)
        val publishedRecord2 = records.firstOrNull { it.first == "by-alias1"}
        assertNotNull(publishedRecord2)
        assertRecord(publishedRecord2.second, original)
        val cachedRecord1 = signingPersistence.get("by-hash1")
        assertNotNull(cachedRecord1)
        assertRecord(cachedRecord1, original)
        val cachedRecord2 = signingPersistence.get("by-alias1")
        assertNotNull(cachedRecord2)
        assertRecord(cachedRecord2, original)
    }

    @Test
    @Timeout(5)
    fun `Should fetch and cache record from subscription when it's not cached yet`() {
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
        kafka.publish(
            KafkaSigningKeysPersistenceProcessor.CLIENT_ID,
            signingPersistence,
            Schemas.Crypto.SIGNING_KEY_PERSISTENCE_TOPIC,
            "hash1",
            original
        )
        val cachedRecord1 = signingPersistence.get("hash1")
        assertNotNull(cachedRecord1)
        assertRecord(cachedRecord1, original)
        // again - will return from cache
        val cachedRecord2 = signingPersistence.get("hash1")
        assertSame(cachedRecord1, cachedRecord2)
    }
}