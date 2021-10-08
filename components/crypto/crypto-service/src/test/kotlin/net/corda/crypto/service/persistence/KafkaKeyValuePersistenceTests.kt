package net.corda.crypto.service.persistence

import net.corda.crypto.impl.config.CryptoLibraryConfigImpl
import net.corda.crypto.impl.persistence.DefaultCryptoCachedKeyInfo
import net.corda.crypto.impl.persistence.DefaultCryptoPersistentKeyInfo
import net.corda.crypto.impl.persistence.KeyValuePersistence
import net.corda.crypto.impl.persistence.KeyValuePersistenceFactory
import net.corda.crypto.impl.persistence.SigningPersistentKeyInfo
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class KafkaKeyValuePersistenceTests {
    private lateinit var memberId: String
    private lateinit var subscriptionFactory: SubscriptionFactory
    private lateinit var publisherFactory: PublisherFactory
    private lateinit var sub: CompactedSubscription<String, SigningPersistentKeyInfo>
    private lateinit var pub: Publisher
    private lateinit var factory: KeyValuePersistenceFactory
    private lateinit var signingPersistence: KeyValuePersistence<SigningPersistentKeyInfo, SigningPersistentKeyInfo>
    private lateinit var defaultPersistence: KeyValuePersistence<DefaultCryptoCachedKeyInfo, DefaultCryptoPersistentKeyInfo>

    @BeforeEach
    fun setup() {
        memberId = UUID.randomUUID().toString()
        subscriptionFactory = mock()
        publisherFactory = mock()
        sub = mock()
        pub = mock()
        val completedFuture = CompletableFuture<Unit>()
        completedFuture.complete(Unit)
        whenever(
            pub.publish(any())
        ).thenReturn(listOf(completedFuture))
        Mockito.`when`(
            subscriptionFactory.createCompactedSubscription(
                any(),
                any<CompactedProcessor<String, SigningPersistentKeyInfo>>(),
                any()
            )
        ).thenReturn(sub)
        Mockito.`when`(
            publisherFactory.createPublisher(
                any(),
                any()
            )
        ).thenReturn(pub)
        factory = KafkaKeyValuePersistenceFactory(
            subscriptionFactory = subscriptionFactory,
            publisherFactory = publisherFactory,
            config = CryptoLibraryConfigImpl(
                mapOf(
                    "keyCache" to mapOf(
                        "persistenceConfig" to mapOf(
                            "groupName" to "groupName",
                            "topicName" to "topicName",
                            "clientId" to "clientId"
                        )
                    ),
                    "mngCache" to mapOf(
                        "persistenceConfig" to mapOf(
                            "groupName" to "groupName",
                            "topicName" to "topicName",
                            "clientId" to "clientId"
                        )

                    )
                )
            )
        )
        signingPersistence = factory.createSigningPersistence(
            memberId = memberId
        ) {
            it
        }
        defaultPersistence = factory.createDefaultCryptoPersistence(
            memberId = memberId
        ) {
            DefaultCryptoCachedKeyInfo(memberId = it.memberId)
        }
    }

    @Test
    fun `Should round trip persist and get signing cache value`() {
        val publishedRecords = argumentCaptor<List<Record<String, SigningPersistentKeyInfo>>>()
        val original = SigningPersistentKeyInfo(
            publicKeyHash = "hash1",
            alias = "alias1",
            publicKey = "Hello World!".toByteArray(),
            memberId = memberId,
            externalId = UUID.randomUUID(),
            masterKeyAlias = "MK",
            privateKeyMaterial = "material".toByteArray(),
            schemeCodeName = "CODE"
        )
        signingPersistence.put("hash1", original)
        Mockito.verify(pub).publish(publishedRecords.capture())
        assertEquals(1, publishedRecords.allValues.size)
        assertEquals(1, publishedRecords.firstValue.size)
        val publishedRecord = publishedRecords.firstValue[0]
        assertNotNull(publishedRecord)
        assertNotNull(publishedRecord.value)
        assertEquals("hash1", publishedRecord.key)
        assertEquals(original.publicKeyHash, publishedRecord.value!!.publicKeyHash)
        assertEquals(original.alias, publishedRecord.value!!.alias)
        assertArrayEquals(original.publicKey, publishedRecord.value!!.publicKey)
        assertEquals(original.memberId, publishedRecord.value!!.memberId)
        assertEquals(original.externalId, publishedRecord.value!!.externalId)
        assertEquals(original.masterKeyAlias, publishedRecord.value!!.masterKeyAlias)
        assertArrayEquals(original.privateKeyMaterial, publishedRecord.value!!.privateKeyMaterial)
        assertEquals(original.schemeCodeName, publishedRecord.value!!.schemeCodeName)
        val cachedRecord = signingPersistence.get("hash1")
        assertNotNull(cachedRecord)
        assertEquals(original.publicKeyHash, cachedRecord.publicKeyHash)
        assertEquals("alias1", cachedRecord.alias)
        assertArrayEquals(original.publicKey, cachedRecord.publicKey)
        assertEquals(original.memberId, cachedRecord.memberId)
        assertEquals(original.externalId, cachedRecord.externalId)
        assertEquals(original.masterKeyAlias, cachedRecord.masterKeyAlias)
        assertArrayEquals(original.privateKeyMaterial, cachedRecord.privateKeyMaterial)
        assertEquals(original.schemeCodeName, cachedRecord.schemeCodeName)
    }

    @Test
    fun `Should get signing cache record from subscription when it's not cached yet`() {
        val original = SigningPersistentKeyInfo(
            publicKeyHash = "hash1",
            alias = "alias1",
            publicKey = "Hello World!".toByteArray(),
            memberId = memberId,
            externalId = UUID.randomUUID(),
            masterKeyAlias = "MK",
            privateKeyMaterial = "material".toByteArray(),
            schemeCodeName = "CODE"
        )
        Mockito.`when`(
            sub.getValue("hash1")
        ).thenReturn(original)
        val cachedRecord1 = signingPersistence.get("hash1")
        assertNotNull(cachedRecord1)
        assertEquals(original.publicKeyHash, cachedRecord1.publicKeyHash)
        assertEquals("alias1", cachedRecord1.alias)
        assertArrayEquals(original.publicKey, cachedRecord1.publicKey)
        assertEquals(original.memberId, cachedRecord1.memberId)
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
        assertEquals(original.memberId, cachedRecord2.memberId)
        assertEquals(original.externalId, cachedRecord2.externalId)
        assertEquals(original.masterKeyAlias, cachedRecord2.masterKeyAlias)
        assertArrayEquals(original.privateKeyMaterial, cachedRecord2.privateKeyMaterial)
        assertEquals(original.schemeCodeName, cachedRecord2.schemeCodeName)
    }

    @Test
    fun `Should get signing cache null when it's not found`() {
        val cachedRecord = signingPersistence.get("hash1")
        assertNull(cachedRecord)
    }

    @Test
    fun `Should get default crypto cache null when it's not found`() {
        val key = "hash1"
        whenever(
            sub.getValue(key)
        ).thenReturn(null)
        val cachedRecord = defaultPersistence.get(key)
        assertNull(cachedRecord)
    }

    @Test
    fun `Should close publisher and subscription`() {
        (factory as AutoCloseable).close()
        // twice - for the signing & crypto proxies
        Mockito.verify(pub, Mockito.times(2)).close()
        Mockito.verify(sub, Mockito.times(2)).close()
    }

    // TODO2 add guarding member id tests
}