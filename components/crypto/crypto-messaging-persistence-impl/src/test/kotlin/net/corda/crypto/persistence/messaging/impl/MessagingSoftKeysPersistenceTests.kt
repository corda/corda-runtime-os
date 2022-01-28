package net.corda.crypto.persistence.messaging.impl

import net.corda.crypto.persistence.EntityKeyInfo
import net.corda.crypto.persistence.KeyValuePersistence
import net.corda.crypto.persistence.CachedSoftKeysRecord
import net.corda.data.crypto.persistence.SoftKeysRecord
import net.corda.schema.Schemas
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

class MessagingSoftKeysPersistenceTests {
    private lateinit var tenantId: String
    private lateinit var kafka: KafkaInfrastructure
    private lateinit var provider: MessagingSoftKeysPersistenceProvider
    private lateinit var persistence: KeyValuePersistence<CachedSoftKeysRecord, SoftKeysRecord>

    @BeforeEach
    fun setup() {
        tenantId = UUID.randomUUID().toString()
        kafka = KafkaInfrastructure()
        provider = kafka.createSoftPersistenceProvider()
        persistence = provider.getInstance(tenantId) {
            CachedSoftKeysRecord(tenantId = it.tenantId)
        }
    }

    @AfterEach
    fun cleanup() {
        (provider as AutoCloseable).close()
        (persistence as AutoCloseable).close()
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
    fun `Should get default crypto cache null when it's not found`() {
        val cachedRecord = persistence.get(UUID.randomUUID().toString())
        assertNull(cachedRecord)
    }

    @Test
    @Timeout(5)
    fun `Should round trip persist and get default crypto cache value`() {
        val alias = UUID.randomUUID().toString()
        val original = SoftKeysRecord(
            tenantId,
            alias,
            ByteBuffer.wrap("Public Key!".toByteArray()),
            ByteBuffer.wrap("Private Key!".toByteArray()),
            "algo",
            1,
            Instant.now()
        )
        persistence.put(original, EntityKeyInfo(EntityKeyInfo.ALIAS, original.alias))
        val records = kafka.getRecords<SoftKeysRecord>(
            MessagingSoftKeysPersistenceProcessor.GROUP_NAME,
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

    @Test
    @Timeout(5)
    fun `Should get default crypto cache record from subscription when it's not cached yet`() {
        val alias = UUID.randomUUID().toString()
        val original = SoftKeysRecord(
            tenantId,
            alias,
            ByteBuffer.wrap("Public Key!".toByteArray()),
            ByteBuffer.wrap("Private Key!".toByteArray()),
            "algo",
            1,
            Instant.now()
        )
        kafka.publish(
            MessagingSoftKeysPersistenceProcessor.CLIENT_ID,
            persistence,
            Schemas.Crypto.SOFT_HSM_PERSISTENCE_TOPIC,
            original.alias,
            original
        )
        val cachedRecord1 = persistence.get(original.alias)
        assertNotNull(cachedRecord1)
        assertEquals(original.tenantId, cachedRecord1.tenantId)
        // again - will return from cache
        val cachedRecord2 = persistence.get(original.alias)
        assertNotNull(cachedRecord2)
        assertSame(cachedRecord1, cachedRecord2)
    }
}