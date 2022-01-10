package net.corda.crypto.service.persistence

import net.corda.crypto.impl.persistence.KeyValuePersistence
import net.corda.crypto.impl.persistence.KeyValuePersistenceFactory
import net.corda.crypto.impl.persistence.SigningKeyRecord
import net.corda.crypto.service.persistence.KafkaInfrastructure.Companion.wait
import net.corda.v5.base.types.toHexString
import net.corda.v5.crypto.sha256Bytes
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class KafkaSigningKeysPersistenceSnapshotTests {
    private lateinit var memberId: String
    private lateinit var kafka: KafkaInfrastructure
    private lateinit var factory: KeyValuePersistenceFactory
    private lateinit var signingPersistence: KeyValuePersistence<SigningKeyRecord, SigningKeyRecord>
    private lateinit var publicKey1: ByteArray
    private lateinit var publicKey2: ByteArray
    private lateinit var original1: SigningKeyRecord
    private lateinit var original2: SigningKeyRecord

    @BeforeEach
    fun setup() {
        memberId = UUID.randomUUID().toString()
        kafka = KafkaInfrastructure()
        publicKey1 = "Hello World1".toByteArray()
        publicKey2 = "Hello World2".toByteArray()
        original1 = SigningKeyRecord(
            keyDerivedId = publicKey1.sha256Bytes().toHexString(),
            alias = "alias1",
            publicKey = publicKey1,
            tenantId = memberId,
            externalId = UUID.randomUUID(),
            masterKeyAlias = "MK",
            privateKeyMaterial = "material1".toByteArray(),
            schemeCodeName = "CODE"
        )
        original2 = SigningKeyRecord(
            keyDerivedId = publicKey2.sha256Bytes().toHexString(),
            alias = "alias2",
            publicKey = publicKey2,
            tenantId = memberId,
            externalId = UUID.randomUUID(),
            masterKeyAlias = "MK",
            privateKeyMaterial = "material2".toByteArray(),
            schemeCodeName = "CODE"
        )

        factory = kafka.createFactory(KafkaInfrastructure.customConfig) {
            kafka.publish<SigningKeyRecord, SigningKeyRecord>(
                KafkaInfrastructure.signingClientId(KafkaInfrastructure.customConfig),
                null,
                KafkaInfrastructure.signingTopicName(KafkaInfrastructure.customConfig),
                original1.keyDerivedId,
                KafkaSigningKeyProxy.toRecord(original1)
            )
            kafka.publish<SigningKeyRecord, SigningKeyRecord>(
                KafkaInfrastructure.signingClientId(KafkaInfrastructure.customConfig),
                null,
                KafkaInfrastructure.signingTopicName(KafkaInfrastructure.customConfig),
                original2.keyDerivedId,
                KafkaSigningKeyProxy.toRecord(original2)
            )
        }
        signingPersistence = factory.createSigningPersistence(
            tenantId = memberId
        ) {
            it
        }
    }

    @AfterEach
    fun cleanup() {
        (factory as AutoCloseable).close()
        (signingPersistence as AutoCloseable).close()
    }

    @Test
    @Timeout(5)
    fun `Should load snapshot and get signing cache value`() {
        val cachedRecord1 =  signingPersistence.wait(original1.keyDerivedId)
        assertNotNull(cachedRecord1)
        assertEquals(original1.keyDerivedId, cachedRecord1.keyDerivedId)
        assertEquals(original1.alias, cachedRecord1.alias)
        assertArrayEquals(original1.publicKey, cachedRecord1.publicKey)
        assertEquals(original1.tenantId, cachedRecord1.tenantId)
        assertEquals(original1.externalId, cachedRecord1.externalId)
        assertEquals(original1.masterKeyAlias, cachedRecord1.masterKeyAlias)
        assertArrayEquals(original1.privateKeyMaterial, cachedRecord1.privateKeyMaterial)
        assertEquals(original1.schemeCodeName, cachedRecord1.schemeCodeName)
        val cachedRecord2 = signingPersistence.get(original2.keyDerivedId)
        assertNotNull(cachedRecord2)
        assertEquals(original2.keyDerivedId, cachedRecord2.keyDerivedId)
        assertEquals(original2.alias, cachedRecord2.alias)
        assertArrayEquals(original2.publicKey, cachedRecord2.publicKey)
        assertEquals(original2.tenantId, cachedRecord2.tenantId)
        assertEquals(original2.externalId, cachedRecord2.externalId)
        assertEquals(original2.masterKeyAlias, cachedRecord2.masterKeyAlias)
        assertArrayEquals(original2.privateKeyMaterial, cachedRecord2.privateKeyMaterial)
        assertEquals(original2.schemeCodeName, cachedRecord2.schemeCodeName)
        val cachedRecord3 = signingPersistence.get(UUID.randomUUID().toString())
        assertNull(cachedRecord3)
    }
}