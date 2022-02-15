package net.corda.crypto.persistence.messaging.impl

import net.corda.crypto.persistence.KeyValuePersistence
import net.corda.crypto.persistence.CachedSoftKeysRecord
import net.corda.crypto.persistence.messaging.impl.KafkaInfrastructure.Companion.wait
import net.corda.data.crypto.persistence.SoftKeysRecord
import net.corda.schema.Schemas
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MessagingSoftKeysPersistenceSnapshotTests {
    private lateinit var tenantId: String
    private lateinit var alias1: String
    private lateinit var alias2: String
    private lateinit var kafka: KafkaInfrastructure
    private lateinit var provider: MessagingSoftKeysPersistenceProvider
    private lateinit var persistence: KeyValuePersistence<CachedSoftKeysRecord, SoftKeysRecord>
    private lateinit var original1: SoftKeysRecord
    private lateinit var original2: SoftKeysRecord

    @BeforeEach
    fun setup() {
        tenantId = UUID.randomUUID().toString()
        alias1 = UUID.randomUUID().toString()
        alias2 = UUID.randomUUID().toString()
        kafka = KafkaInfrastructure()
        original1 = SoftKeysRecord(
            tenantId,
            alias1,
            ByteBuffer.wrap("Public Key1".toByteArray()),
            ByteBuffer.wrap("Private Key1".toByteArray()),
            "algo",
            1,
            Instant.now()
        )
        original2 = SoftKeysRecord(
            tenantId,
            alias2,
            ByteBuffer.wrap("Public Key2".toByteArray()),
            ByteBuffer.wrap("Private Key2".toByteArray()),
            "algo",
            1,
            Instant.now()
        )
        provider = kafka.createSoftPersistenceProvider {
            kafka.publish<CachedSoftKeysRecord, SoftKeysRecord>(
                MessagingSoftKeysPersistenceProcessor.CLIENT_ID,
                null,
                Schemas.Crypto.SOFT_HSM_PERSISTENCE_TOPIC,
                original1.alias,
                original1
            )
            kafka.publish<CachedSoftKeysRecord, SoftKeysRecord>(
                MessagingSoftKeysPersistenceProcessor.CLIENT_ID,
                null,
                Schemas.Crypto.SOFT_HSM_PERSISTENCE_TOPIC,
                original2.alias,
                original2
            )
        }
        persistence = provider.getInstance(tenantId) {
            // just to be able to assert
            CachedSoftKeysRecord(tenantId = it.alias)
        }
    }

    @AfterEach
    fun cleanup() {
        (provider as AutoCloseable).close()
        (persistence as AutoCloseable).close()
    }

    @Test
    @Timeout(300)
    fun `Should load snapshot and get default crypto cache value`() {
        val cachedRecord1 = persistence.wait(original1.alias)
        assertNotNull(cachedRecord1)
        assertEquals(original1.alias, cachedRecord1.tenantId)
        val cachedRecord2 = persistence.get(original2.alias)
        assertNotNull(cachedRecord2)
        assertEquals(original2.alias, cachedRecord2.tenantId)
        val cachedRecord3 = persistence.get(UUID.randomUUID().toString())
        assertNull(cachedRecord3)
    }
}