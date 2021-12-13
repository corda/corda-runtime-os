package net.corda.crypto.component.config

import net.corda.crypto.impl.config.CryptoLibraryConfigImpl
import net.corda.crypto.impl.config.DefaultConfigConsts
import net.corda.crypto.impl.config.memberConfig
import net.corda.data.crypto.config.CryptoConfigurationRecord
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.CompactedSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.emulation.publisher.factory.CordaPublisherFactory
import net.corda.messaging.emulation.rpc.RPCTopicService
import net.corda.messaging.emulation.rpc.RPCTopicServiceImpl
import net.corda.messaging.emulation.subscription.factory.InMemSubscriptionFactory
import net.corda.messaging.emulation.topic.service.TopicService
import net.corda.messaging.emulation.topic.service.impl.TopicServiceImpl
import net.corda.v5.cipher.suite.config.CryptoLibraryConfig
import net.corda.v5.cipher.suite.config.CryptoMemberConfig
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.empty
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

class MemberConfigReaderTests {
    companion object {
        private const val TOPIC_NAME = "topic"
        private const val GROUP_NAME = "groupName"
        private const val CLIENT_ID = "clientId"
        private val defaultConfig: CryptoLibraryConfig = CryptoLibraryConfigImpl(emptyMap())
        private val customConfig: CryptoLibraryConfig = CryptoLibraryConfigImpl(
            mapOf(
                "memberConfig" to mapOf(
                    DefaultConfigConsts.Kafka.GROUP_NAME_KEY to GROUP_NAME,
                    DefaultConfigConsts.Kafka.TOPIC_NAME_KEY to TOPIC_NAME,
                    DefaultConfigConsts.Kafka.CLIENT_ID_KEY to CLIENT_ID
                )
            )
        )
        private val cryptoMemberConfig = CryptoMemberConfigImpl(
            mapOf(
                "default" to mapOf(
                    "serviceName" to "default",
                    "timeout" to "1",
                    "defaultSignatureScheme" to "CORDA.ECDSA.SECP256K1",
                    "serviceConfig" to mapOf(
                        "passphrase" to "pwdD",
                        "salt" to "saltD"
                    )
                ),
                "LEDGER" to mapOf(
                    "serviceName" to "FUTUREX",
                    "timeout" to "10",
                    "defaultSignatureScheme" to "CORDA.EDDSA.ED25519",
                    "serviceConfig" to mapOf(
                        "credentials" to "password"
                    )
                )
            )
        )
        private val cryptoMemberConfig2 = CryptoMemberConfigImpl(
            mapOf(
                "default" to mapOf(
                    "serviceName" to "FUTUREX",
                    "timeout" to "2",
                    "defaultSignatureScheme" to "CORDA.ECDSA.SECP256K1",
                    "serviceConfig" to mapOf(
                        "credentials" to "password"
                    )
                )
            )
        )
        private val cryptoMemberConfig3 = CryptoMemberConfigImpl(
            mapOf(
                "LEDGER" to mapOf(
                    "serviceName" to "FUTUREX",
                    "timeout" to "10",
                    "defaultSignatureScheme" to "CORDA.EDDSA.ED25519",
                    "serviceConfig" to mapOf(
                        "credentials" to "password"
                    )
                )
            )
        )

        private fun CompactedSubscription<String, CryptoConfigurationRecord>.wait(
            key: String,
            timeout: Duration = Duration.ofSeconds(2),
            retryDelay: Duration = Duration.ofMillis(50),
        ): CryptoConfigurationRecord = wait(key, timeout, retryDelay) { this.getValue(it) }

        private fun MemberConfigReaderImpl.wait(
            key: String,
            timeout: Duration = Duration.ofSeconds(2),
            retryDelay: Duration = Duration.ofMillis(50),
        ): CryptoMemberConfig = wait(key, timeout, retryDelay) {
            val value = this.get(it)
            if (value.isEmpty()) {
                null
            } else {
                value
            }
        }

        private fun <T> wait(key: String, timeout: Duration, retryDelay: Duration, get: (String) -> T?): T {
            val end = Instant.now().plus(timeout)
            do {
                try {
                    val value = get(key)
                    if (value != null) {
                        return value
                    }
                } catch (e: Exception) {
                    // intentional
                }
                Thread.sleep(retryDelay.toMillis())
            } while (Instant.now() < end)
            fail("Failed to wait for '$key'")
        }
    }

    private lateinit var topicService: TopicService
    private lateinit var rpcTopicService: RPCTopicService
    private lateinit var subscriptionFactory: SubscriptionFactory
    private lateinit var publisherFactory: PublisherFactory
    private lateinit var reader: MemberConfigReaderImpl
    private var lifecycleCoordinator: LifecycleCoordinator = mock()
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), any()) } doReturn lifecycleCoordinator
    }

    @BeforeEach
    fun setup() {
        topicService = TopicServiceImpl()
        rpcTopicService = RPCTopicServiceImpl(Executors.newCachedThreadPool())
        subscriptionFactory = InMemSubscriptionFactory(topicService, rpcTopicService, lifecycleCoordinatorFactory)
        publisherFactory = CordaPublisherFactory(topicService, rpcTopicService, lifecycleCoordinatorFactory)
        reader = MemberConfigReaderImpl(
            subscriptionFactory
        )
    }

    @AfterEach
    fun cleanup() {
        reader.close()
    }

    private fun publish(config: CryptoLibraryConfig, vararg records: Pair<String, CryptoMemberConfig>) {
        val writer = MemberConfigWriterImpl(publisherFactory)
        writer.start()
        writer.handleConfigEvent(config)
        records.forEach {
            writer.put(it.first, it.second).get()
        }
        val subscription = subscriptionFactory.createCompactedSubscription(
            SubscriptionConfig(
                config.memberConfig.getString(
                    DefaultConfigConsts.Kafka.GROUP_NAME_KEY,
                    DefaultConfigConsts.Kafka.MemberConfig.GROUP_NAME
                ),
                config.memberConfig.getString(
                    DefaultConfigConsts.Kafka.TOPIC_NAME_KEY,
                    DefaultConfigConsts.Kafka.MemberConfig.TOPIC_NAME
                )
            ),
            NoOpProcessor()
        ).also { it.start() }
        records.forEach {
            subscription.wait(it.first)
        }
        writer.close()
    }

    @Test
    @Timeout(5)
    fun `Should return default value when reader is not configured`() {
        publish(customConfig, "123" to cryptoMemberConfig, "789" to cryptoMemberConfig3)
        reader.start()
        val result = reader.get("123")
        assertNotNull(result)
        assertEquals(0, result.size)
    }

    @Test
    @Timeout(5)
    fun `Should load snapshot and get configuration value`() {
        publish(customConfig, "123" to cryptoMemberConfig, "789" to cryptoMemberConfig3)
        reader.start()
        reader.handleConfigEvent(customConfig)
        val result = reader.wait("123")
        assertNotNull(result)
        assertEquals(2, result.size)
        assertThat(result.keys, contains("default", "LEDGER"))
    }

    @Test
    @Timeout(5)
    fun `Should load snapshot using default configuration and get configuration value`() {
        publish(defaultConfig, "123" to cryptoMemberConfig, "789" to cryptoMemberConfig3)
        reader.start()
        reader.handleConfigEvent(defaultConfig)
        val result = reader.wait("123")
        assertNotNull(result)
        assertEquals(2, result.size)
        assertThat(result.keys, contains("default", "LEDGER"))
    }

    @Test
    @Timeout(5)
    fun `Should load snapshot then update and get new configuration value`() {
        publish(customConfig, "123" to cryptoMemberConfig, "789" to cryptoMemberConfig3)
        reader.start()
        reader.handleConfigEvent(customConfig)
        val result = reader.wait("123")
        assertNotNull(result)
        assertEquals(2, result.size)
        assertThat(result.keys, contains("default", "LEDGER"))
        publish(customConfig, "123" to cryptoMemberConfig2)
        val result2 = reader.wait("123")
        assertNotNull(result2)
        assertEquals(1, result2.size)
        assertThat(result2.keys, contains("default"))
    }

    @Test
    @Timeout(5)
    fun `Should add and get new configuration value`() {
        reader.start()
        reader.handleConfigEvent(customConfig)
        publish(customConfig, "123" to cryptoMemberConfig, "789" to cryptoMemberConfig3)
        val result = reader.wait("123")
        assertNotNull(result)
        assertEquals(2, result.size)
        assertThat(result.keys, contains("default", "LEDGER"))
        publish(customConfig, "123" to cryptoMemberConfig2)
    }

    @Test
    @Timeout(5)
    fun `Should add and get new configuration value using default configuration`() {
        reader.start()
        reader.handleConfigEvent(defaultConfig)
        publish(defaultConfig, "123" to cryptoMemberConfig, "789" to cryptoMemberConfig3)
        val result = reader.wait("123")
        assertNotNull(result)
        assertEquals(2, result.size)
        assertThat(result.keys, contains("default", "LEDGER"))
        publish(defaultConfig, "123" to cryptoMemberConfig2)
    }

    @Test
    @Timeout(5)
    fun `Should return default value if the cache is empty`() {
        reader.start()
        reader.handleConfigEvent(customConfig)
        val result = reader.get("123")
        assertNotNull(result)
        assertThat(result.keys, empty())
    }

    @Test
    @Timeout(5)
    fun `Should return default value if the config for member is not found`() {
        publish(customConfig, "123" to cryptoMemberConfig)
        reader.start()
        reader.handleConfigEvent(customConfig)
        reader.wait("123")
        val result = reader.get("789")
        assertNotNull(result)
        assertThat(result.keys, empty())
    }

    @Test
    @Timeout(5)
    fun `Should close initialised subscription`() {
        val sub = mock<CompactedSubscription<String, CryptoConfigurationRecord>>()
        val factory: SubscriptionFactory = mock {
            on { createCompactedSubscription<String, CryptoConfigurationRecord>(any(), any(), any()) }.thenReturn(sub)
        }
        val reader = MemberConfigReaderImpl(factory)
        reader.start()
        reader.handleConfigEvent(customConfig)
        Mockito.verify(sub, never()).close()
        reader.close()
        Mockito.verify(sub, times(1)).close()
    }

    @Test
    @Timeout(5)
    fun `Should close initialised subscription when component is reconfigured`() {
        val sub = mock<CompactedSubscription<String, CryptoConfigurationRecord>>()
        val factory: SubscriptionFactory = mock {
            on { createCompactedSubscription<String, CryptoConfigurationRecord>(any(), any(), any()) }.thenReturn(sub)
        }
        val reader = MemberConfigReaderImpl(factory)
        reader.start()
        reader.handleConfigEvent(customConfig)
        Mockito.verify(sub, never()).close()
        reader.handleConfigEvent(customConfig)
        Mockito.verify(sub, times(1)).close()
        reader.close()
        Mockito.verify(sub, times(2)).close()
    }

    @Test
    @Timeout(5)
    fun `Should not fail closing uninitialised reader`() {
        val factory: SubscriptionFactory = mock()
        val reader = MemberConfigReaderImpl(factory)
        Mockito.verify(factory, never())
            .createCompactedSubscription<String, CryptoConfigurationRecord>(any(), any(), any())
        reader.close()
        Mockito.verify(factory, never())
            .createCompactedSubscription<String, CryptoConfigurationRecord>(any(), any(), any())
    }

    private class NoOpProcessor : CompactedProcessor<String, CryptoConfigurationRecord> {
        override val keyClass: Class<String> = String::class.java
        override val valueClass: Class<CryptoConfigurationRecord> = CryptoConfigurationRecord::class.java
        override fun onSnapshot(currentData: Map<String, CryptoConfigurationRecord>) {
        }

        override fun onNext(
            newRecord: Record<String, CryptoConfigurationRecord>,
            oldValue: CryptoConfigurationRecord?,
            currentData: Map<String, CryptoConfigurationRecord>
        ) {
        }
    }
}