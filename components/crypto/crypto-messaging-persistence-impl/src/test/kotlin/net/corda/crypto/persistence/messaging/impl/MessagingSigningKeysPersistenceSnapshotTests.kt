package net.corda.crypto.persistence.messaging.impl

import net.corda.crypto.CryptoConsts
import net.corda.crypto.persistence.KeyValuePersistence
import net.corda.crypto.persistence.messaging.impl.KafkaInfrastructure.Companion.wait
import net.corda.data.crypto.persistence.SigningKeysRecord
import net.corda.schema.Schemas
import net.corda.v5.crypto.calculateHash
import net.corda.v5.crypto.sha256Bytes
import org.bouncycastle.util.encoders.Base32
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.nio.ByteBuffer
import java.security.PublicKey
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class MessagingSigningKeysPersistenceSnapshotTests {
    private lateinit var tenantId: String
    private lateinit var kafka: KafkaInfrastructure
    private lateinit var provider: MessagingSigningKeysPersistenceProvider
    private lateinit var signingPersistence: KeyValuePersistence<SigningKeysRecord, SigningKeysRecord>
    private lateinit var alias1: String
    private lateinit var keyDerivedId1: String
    private lateinit var publicKey1: PublicKey
    private lateinit var original1: SigningKeysRecord
    private lateinit var alias2: String
    private lateinit var keyDerivedId2: String
    private lateinit var aliasDerivedId2: String
    private lateinit var publicKey2: PublicKey
    private lateinit var original2: SigningKeysRecord

    @BeforeEach
    fun setup() {
        tenantId = UUID.randomUUID().toString()
        publicKey1 = mock {
            on { encoded } doReturn "Hello World 1".toByteArray()
        }
        publicKey2 = mock {
            on { encoded } doReturn "Hello World 2".toByteArray()
        }
        alias1 = UUID.randomUUID().toString()
        alias2 = UUID.randomUUID().toString()
        keyDerivedId1 = "$tenantId:${publicKey1.calculateHash()}"
        keyDerivedId2 = "$tenantId:${publicKey2.calculateHash()}"
        aliasDerivedId2 = "$tenantId:$alias2"
        kafka = KafkaInfrastructure()
        original1 = SigningKeysRecord(
            tenantId,
            CryptoConsts.Categories.LEDGER,
            alias1,
            Base32.toBase32String((tenantId + alias1).encodeToByteArray().sha256Bytes()).take(30).toLowerCase(),
            ByteBuffer.wrap(publicKey1.encoded),
            ByteBuffer.wrap("material1".toByteArray()),
            "CODE",
            "MK",
            UUID.randomUUID().toString(),
            1,
            Instant.now()
        )
        original2 = SigningKeysRecord(
            tenantId,
            CryptoConsts.Categories.LEDGER,
            alias2,
            Base32.toBase32String((tenantId + alias2).encodeToByteArray().sha256Bytes()).take(30).toLowerCase(),
            ByteBuffer.wrap(publicKey2.encoded),
            null,
            "CODE",
            null,
            null,
            1,
            Instant.now()
        )
        provider = kafka.createSigningKeysPersistenceProvider {
            kafka.publish<SigningKeysRecord, SigningKeysRecord>(
                MessagingSigningKeysPersistenceProcessor.CLIENT_ID,
                null,
                Schemas.Crypto.SIGNING_KEY_PERSISTENCE_TOPIC,
                keyDerivedId1,
                original1
            )
            kafka.publish<SigningKeysRecord, SigningKeysRecord>(
                MessagingSigningKeysPersistenceProcessor.CLIENT_ID,
                null,
                Schemas.Crypto.SIGNING_KEY_PERSISTENCE_TOPIC,
                keyDerivedId2,
                original2
            )
            kafka.publish<SigningKeysRecord, SigningKeysRecord>(
                MessagingSigningKeysPersistenceProcessor.CLIENT_ID,
                null,
                Schemas.Crypto.SIGNING_KEY_PERSISTENCE_TOPIC,
                aliasDerivedId2,
                original2
            )
        }
        signingPersistence = provider.getInstance(tenantId) { it }
    }

    @AfterEach
    fun cleanup() {
        (provider as AutoCloseable).close()
        (signingPersistence as AutoCloseable).close()
    }

    @Test
    @Timeout(10)
    fun `Should load snapshot and get signing cache value`() {
        val cachedRecord1 =  signingPersistence.wait(keyDerivedId1)
        assertNotNull(cachedRecord1)
        assertEquals(original1.tenantId, cachedRecord1.tenantId)
        assertEquals(original1.category, cachedRecord1.category)
        assertEquals(original1.alias, cachedRecord1.alias)
        assertEquals(original1.hsmAlias, cachedRecord1.hsmAlias)
        assertArrayEquals(original1.publicKey.array(), cachedRecord1.publicKey.array())
        assertEquals(original1.tenantId, cachedRecord1.tenantId)
        assertEquals(original1.externalId, cachedRecord1.externalId)
        assertEquals(original1.masterKeyAlias, cachedRecord1.masterKeyAlias)
        assertArrayEquals(original1.privateKeyMaterial?.array(), cachedRecord1.privateKeyMaterial?.array())
        assertEquals(original1.schemeCodeName, cachedRecord1.schemeCodeName)
        val cachedRecord2 = signingPersistence.get(keyDerivedId2)
        assertNotNull(cachedRecord2)
        assertEquals(original2.tenantId, cachedRecord2.tenantId)
        assertEquals(original2.category, cachedRecord2.category)
        assertEquals(original2.alias, cachedRecord2.alias)
        assertEquals(original2.hsmAlias, cachedRecord2.hsmAlias)
        assertArrayEquals(original2.publicKey.array(), cachedRecord2.publicKey.array())
        assertEquals(original2.tenantId, cachedRecord2.tenantId)
        assertEquals(original2.externalId, cachedRecord2.externalId)
        assertEquals(original2.masterKeyAlias, cachedRecord2.masterKeyAlias)
        assertArrayEquals(original2.privateKeyMaterial?.array(), cachedRecord2.privateKeyMaterial?.array())
        assertEquals(original2.schemeCodeName, cachedRecord2.schemeCodeName)
        val cachedRecord3 = signingPersistence.get(aliasDerivedId2)
        assertSame(cachedRecord2, cachedRecord3)
        val randomRecord = signingPersistence.get(UUID.randomUUID().toString())
        assertNull(randomRecord)
    }
}