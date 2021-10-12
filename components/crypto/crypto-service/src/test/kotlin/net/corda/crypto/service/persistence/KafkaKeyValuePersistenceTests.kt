package net.corda.crypto.service.persistence

import com.typesafe.config.ConfigFactory
import net.corda.crypto.impl.config.CryptoLibraryConfigImpl
import net.corda.crypto.impl.persistence.DefaultCryptoCachedKeyInfo
import net.corda.crypto.impl.persistence.DefaultCryptoPersistentKeyInfo
import net.corda.crypto.impl.persistence.IHaveMemberId
import net.corda.crypto.impl.persistence.KeyValuePersistence
import net.corda.crypto.impl.persistence.KeyValuePersistenceFactory
import net.corda.crypto.impl.persistence.SigningPersistentKeyInfo
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.emulation.publisher.factory.CordaPublisherFactory
import net.corda.messaging.emulation.subscription.factory.InMemSubscriptionFactory
import net.corda.messaging.emulation.topic.service.TopicService
import net.corda.messaging.emulation.topic.service.impl.TopicServiceImpl
import net.corda.v5.cipher.suite.config.CryptoLibraryConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

class KafkaKeyValuePersistenceTests {
    companion object {
        const val KEY_CACHE_TOPIC_NAME = "keyCacheTopic"
        const val MNG_CACHE_TOPIC_NAME = "mngCacheTopic"
        const val GROUP_NAME = "groupName"
        const val CLIENT_ID = "clientId"
    }

    private lateinit var memberId: String
    private lateinit var memberId2: String
    private lateinit var topicService: TopicService
    private lateinit var subscriptionFactory: SubscriptionFactory
    private lateinit var publisherFactory: PublisherFactory
    private lateinit var factory: KeyValuePersistenceFactory
    private lateinit var signingPersistence: KeyValuePersistence<SigningPersistentKeyInfo, SigningPersistentKeyInfo>
    private lateinit var signingPersistence2: KeyValuePersistence<SigningPersistentKeyInfo, SigningPersistentKeyInfo>
    private lateinit var defaultPersistence: KeyValuePersistence<DefaultCryptoCachedKeyInfo, DefaultCryptoPersistentKeyInfo>
    private lateinit var defaultPersistence2: KeyValuePersistence<DefaultCryptoCachedKeyInfo, DefaultCryptoPersistentKeyInfo>
    private lateinit var config: CryptoLibraryConfig

    @BeforeEach
    fun setup() {
        memberId = UUID.randomUUID().toString()
        memberId2 = UUID.randomUUID().toString()
        topicService = TopicServiceImpl()
        subscriptionFactory = InMemSubscriptionFactory(topicService)
        publisherFactory = CordaPublisherFactory(topicService)
        config = CryptoLibraryConfigImpl(
            mapOf(
                "keyCache" to mapOf(
                    "persistenceConfig" to mapOf(
                        "groupName" to GROUP_NAME,
                        "topicName" to KEY_CACHE_TOPIC_NAME,
                        "clientId" to CLIENT_ID
                    )
                ),
                "mngCache" to mapOf(
                    "persistenceConfig" to mapOf(
                        "groupName" to GROUP_NAME,
                        "topicName" to MNG_CACHE_TOPIC_NAME,
                        "clientId" to CLIENT_ID
                    )

                )
            )
        )
        factory = KafkaKeyValuePersistenceFactory(
            subscriptionFactory = subscriptionFactory,
            publisherFactory = publisherFactory,
            config = config
        )
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
        defaultPersistence = factory.createDefaultCryptoPersistence(
            memberId = memberId
        ) {
            DefaultCryptoCachedKeyInfo(memberId = it.memberId)
        }
        defaultPersistence2 = factory.createDefaultCryptoPersistence(
            memberId = memberId2
        ) {
            DefaultCryptoCachedKeyInfo(memberId = it.memberId)
        }
    }

    @AfterEach
    fun cleanup() {
        (factory as AutoCloseable).close()
        (signingPersistence as AutoCloseable).close()
        (defaultPersistence as AutoCloseable).close()
    }

    private inline fun <reified E : Any> getRecords(topic: String, expectedCount: Int = 1): List<Pair<String, E>> {
        val stop = CountDownLatch(expectedCount)
        val records = mutableListOf<Pair<String, E>>()
        subscriptionFactory.createCompactedSubscription(
            subscriptionConfig = SubscriptionConfig(GROUP_NAME, topic),
            processor = object : CompactedProcessor<String, E> {
                override val keyClass: Class<String> = String::class.java
                override val valueClass: Class<E> = E::class.java
                override fun onSnapshot(currentData: Map<String, E>) {
                    records.addAll(currentData.entries.map { it.key to it.value })
                    repeat(currentData.count()) {
                        stop.countDown()
                    }
                }

                override fun onNext(newRecord: Record<String, E>, oldValue: E?, currentData: Map<String, E>) {
                    records.add(newRecord.key to newRecord.value!!)
                    stop.countDown()
                }
            },
            nodeConfig = ConfigFactory.empty()
        ).use {
            it.start()
            stop.await(2, TimeUnit.SECONDS)
        }
        return records.toList()
    }

    private inline fun <reified V : IHaveMemberId, E : IHaveMemberId> KeyValuePersistence<V, E>.wait(key: String) {
        val started = Instant.now()
        while (get(key) == null) {
            Thread.sleep(100)
            if (Duration.between(started, Instant.now()).seconds > 2) {
                fail("Failed to wait for '$key'")
            }
        }
    }

    private inline fun <reified V : IHaveMemberId, E : IHaveMemberId> KeyValuePersistence<V, E>.publish(
        topic: String,
        key: String,
        original: E
    ) {
        val pub = publisherFactory.createPublisher(PublisherConfig(CLIENT_ID))
        pub.publish(
            listOf(Record(topic, key, original))
        )[0].get()
        wait(key)
        pub.close()
    }

    private fun assertPublishedRecord(
        publishedRecord: Pair<String, SigningPersistentKeyInfo>,
        original: SigningPersistentKeyInfo
    ) {
        assertNotNull(publishedRecord.second)
        assertEquals(original.publicKeyHash, publishedRecord.first)
        assertEquals(original.publicKeyHash, publishedRecord.second.publicKeyHash)
        assertEquals(original.alias, publishedRecord.second.alias)
        assertArrayEquals(original.publicKey, publishedRecord.second.publicKey)
        assertEquals(original.memberId, publishedRecord.second.memberId)
        assertEquals(original.externalId, publishedRecord.second.externalId)
        assertEquals(original.masterKeyAlias, publishedRecord.second.masterKeyAlias)
        assertArrayEquals(original.privateKeyMaterial, publishedRecord.second.privateKeyMaterial)
        assertEquals(original.schemeCodeName, publishedRecord.second.schemeCodeName)
    }

    private fun assertPublishedRecord(
        publishedRecord: Pair<String, DefaultCryptoPersistentKeyInfo>,
        original: DefaultCryptoPersistentKeyInfo
    ) {
        assertNotNull(publishedRecord.second)
        assertEquals(original.alias, publishedRecord.first)
        assertEquals(original.alias, publishedRecord.second.alias)
        assertEquals(original.memberId, publishedRecord.second.memberId)
        assertArrayEquals(original.publicKey, publishedRecord.second.publicKey)
        assertArrayEquals(original.privateKey, publishedRecord.second.privateKey)
        assertEquals(original.algorithmName, publishedRecord.second.algorithmName)
        assertEquals(original.version, publishedRecord.second.version)
    }

    @Test
    fun `Should round trip persist and get signing cache value`() {
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
        val records = getRecords<SigningPersistentKeyInfo>(MNG_CACHE_TOPIC_NAME)
        assertEquals(1, records.size)
        val publishedRecord = records[0]
        assertPublishedRecord(publishedRecord, original)
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
    fun `Should filter signing cache values based on member id`() {
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
        val original2 = SigningPersistentKeyInfo(
            publicKeyHash = "hash2",
            alias = "alias1",
            publicKey = "Hello World2!".toByteArray(),
            memberId = memberId2,
            externalId = UUID.randomUUID(),
            masterKeyAlias = "MK",
            privateKeyMaterial = "material2".toByteArray(),
            schemeCodeName = "CODE2"
        )
        signingPersistence.put(original.publicKeyHash, original)
        signingPersistence2.put(original2.publicKeyHash, original2)
        val records = getRecords<SigningPersistentKeyInfo>(MNG_CACHE_TOPIC_NAME, 2)
        assertEquals(2, records.size)
        val publishedRecord = records.first { it.second.publicKeyHash == original.publicKeyHash }
        val publishedRecord2 = records.first { it.second.publicKeyHash == original2.publicKeyHash }
        assertPublishedRecord(publishedRecord, original)
        assertPublishedRecord(publishedRecord2, original2)
        val cachedRecord = signingPersistence.get(original.publicKeyHash)
        assertNotNull(cachedRecord)
        assertEquals(original.publicKeyHash, cachedRecord.publicKeyHash)
        assertEquals("alias1", cachedRecord.alias)
        assertArrayEquals(original.publicKey, cachedRecord.publicKey)
        assertEquals(original.memberId, cachedRecord.memberId)
        assertEquals(original.externalId, cachedRecord.externalId)
        assertEquals(original.masterKeyAlias, cachedRecord.masterKeyAlias)
        assertArrayEquals(original.privateKeyMaterial, cachedRecord.privateKeyMaterial)
        assertEquals(original.schemeCodeName, cachedRecord.schemeCodeName)
        val cachedRecord2 = signingPersistence2.get(original2.publicKeyHash)
        assertNotNull(cachedRecord2)
        assertEquals(original2.publicKeyHash, cachedRecord2.publicKeyHash)
        assertEquals("alias1", cachedRecord2.alias)
        assertArrayEquals(original2.publicKey, cachedRecord2.publicKey)
        assertEquals(original2.memberId, cachedRecord2.memberId)
        assertEquals(original2.externalId, cachedRecord2.externalId)
        assertEquals(original2.masterKeyAlias, cachedRecord2.masterKeyAlias)
        assertArrayEquals(original2.privateKeyMaterial, cachedRecord2.privateKeyMaterial)
        assertEquals(original2.schemeCodeName, cachedRecord2.schemeCodeName)
        assertNull(signingPersistence.get(original2.publicKeyHash))
        assertNull(signingPersistence2.get(original.publicKeyHash))
    }

    @Test
    fun `Should round trip persist and get default crypto cache value`() {
        // alias is prefixed by the member id when it's used by the DefaultCryptoKeyCacheImpl
        val original = DefaultCryptoPersistentKeyInfo(
            alias = "$memberId:alias1",
            publicKey = "Public Key!".toByteArray(),
            memberId = memberId,
            privateKey = "Private Key!".toByteArray(),
            algorithmName = "algo",
            version = 2
        )
        defaultPersistence.put(original.alias, original)
        val records = getRecords<DefaultCryptoPersistentKeyInfo>(KEY_CACHE_TOPIC_NAME)
        assertEquals(1, records.size)
        val publishedRecord = records[0]
        assertPublishedRecord(publishedRecord, original)
        val cachedRecord = defaultPersistence.get(original.alias)
        assertNotNull(cachedRecord)
        assertEquals(original.memberId, cachedRecord.memberId)
    }

    @Test
    fun `Should filter default crypto cache values based on member id`() {
        // alias is prefixed by the member id when it's used by the DefaultCryptoKeyCacheImpl
        val original = DefaultCryptoPersistentKeyInfo(
            alias = "$memberId:alias1",
            publicKey = "Public Key!".toByteArray(),
            memberId = memberId,
            privateKey = "Private Key!".toByteArray(),
            algorithmName = "algo",
            version = 2
        )
        val original2 = DefaultCryptoPersistentKeyInfo(
            alias = "$memberId2:alias1",
            publicKey = "Public Key2!".toByteArray(),
            memberId = memberId2,
            privateKey = "Private Key2!".toByteArray(),
            algorithmName = "algo",
            version = 2
        )
        defaultPersistence.put(original.alias, original)
        defaultPersistence2.put(original2.alias, original2)
        val records = getRecords<DefaultCryptoPersistentKeyInfo>(KEY_CACHE_TOPIC_NAME, 2)
        assertEquals(2, records.size)
        val publishedRecord = records.first { it.second.alias == original.alias }
        val publishedRecord2 = records.first { it.second.alias == original2.alias }
        assertPublishedRecord(publishedRecord, original)
        assertPublishedRecord(publishedRecord2, original2)
        val cachedRecord = defaultPersistence.get(original.alias)
        assertNotNull(cachedRecord)
        assertEquals(original.memberId, cachedRecord.memberId)
        val cachedRecord2 = defaultPersistence2.get(original2.alias)
        assertNotNull(cachedRecord2)
        assertEquals(original2.memberId, cachedRecord2.memberId)
        assertNull(defaultPersistence.get(original2.alias))
        assertNull(defaultPersistence2.get(original.alias))
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
        signingPersistence.publish(MNG_CACHE_TOPIC_NAME, "hash1", original)
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
    fun `Should get default crypto cache record from subscription when it's not cached yet`() {
        // alias is prefixed by the member id when it's used by the DefaultCryptoKeyCacheImpl
        val original = DefaultCryptoPersistentKeyInfo(
            alias = "$memberId:alias1",
            publicKey = "Public Key!".toByteArray(),
            memberId = memberId,
            privateKey = "Private Key!".toByteArray(),
            algorithmName = "algo",
            version = 2
        )
        defaultPersistence.publish(KEY_CACHE_TOPIC_NAME, original.alias, original)
        val cachedRecord1 = defaultPersistence.get(original.alias)
        assertNotNull(cachedRecord1)
        assertEquals(original.memberId, cachedRecord1.memberId)
        // again - will return from cache
        val cachedRecord2 = defaultPersistence.get(original.alias)
        assertNotNull(cachedRecord2)
        assertEquals(original.memberId, cachedRecord2.memberId)
    }

    @Test
    fun `Should get signing cache null when it's not found`() {
        val cachedRecord = signingPersistence.get("hash1")
        assertNull(cachedRecord)
    }

    @Test
    fun `Should get default crypto cache null when it's not found`() {
        val cachedRecord = defaultPersistence.get("$memberId:alias1")
        assertNull(cachedRecord)
    }

    @Test
    fun `Should close publisher and subscription`() {
        val subscriptionFactory = mock<SubscriptionFactory>()
        val publisherFactory = mock<PublisherFactory>()
        val sub = mock<CompactedSubscription<String, SigningPersistentKeyInfo>>()
        val pub = mock<Publisher>()
        whenever(
            subscriptionFactory.createCompactedSubscription(
                any(),
                any<CompactedProcessor<String, SigningPersistentKeyInfo>>(),
                any()
            )
        ).thenReturn(sub)
        whenever(
            publisherFactory.createPublisher(any(), any())
        ).thenReturn(pub)
        val factory = KafkaKeyValuePersistenceFactory(
            subscriptionFactory = subscriptionFactory,
            publisherFactory = publisherFactory,
            config = config
        )
        (factory as AutoCloseable).close()
        // twice - for the signing & crypto proxies
        Mockito.verify(pub, Mockito.times(2)).close()
        Mockito.verify(sub, Mockito.times(2)).close()
    }

    @Test
    fun `Should create subscriptions only once`() {
        val subscriptionFactory = mock<SubscriptionFactory>()
        val publisherFactory = mock<PublisherFactory>()
        val sub = mock<CompactedSubscription<String, SigningPersistentKeyInfo>>()
        val pub = mock<Publisher>()
        whenever(
            subscriptionFactory.createCompactedSubscription(
                any(),
                any<CompactedProcessor<*, *>>(),
                any()
            )
        ).thenReturn(sub)
        whenever(
            publisherFactory.createPublisher(any(), any())
        ).thenReturn(pub)
        val factory = KafkaKeyValuePersistenceFactory(
            subscriptionFactory = subscriptionFactory,
            publisherFactory = publisherFactory,
            config = config
        )
        factory.createSigningPersistence(memberId) { it }
        factory.createDefaultCryptoPersistence(UUID.randomUUID().toString()) {
            DefaultCryptoCachedKeyInfo(memberId)
        }
        factory.createSigningPersistence(memberId) { it }
        factory.createDefaultCryptoPersistence(UUID.randomUUID().toString()) {
            DefaultCryptoCachedKeyInfo(memberId)
        }
        factory.createSigningPersistence(memberId) { it }
        factory.createDefaultCryptoPersistence(UUID.randomUUID().toString()) {
            DefaultCryptoCachedKeyInfo(memberId)
        }
        (factory as AutoCloseable).close()
        // twice - for the signing & crypto proxies, and that's it
        Mockito.verify(pub, Mockito.times(2)).close()
        Mockito.verify(sub, Mockito.times(2)).close()
    }
}