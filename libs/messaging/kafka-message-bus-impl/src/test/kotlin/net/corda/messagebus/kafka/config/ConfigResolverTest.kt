package net.corda.messaging.kafka.subscription.net.corda.messagebus.kafka.config

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.messagebus.api.configuration.ConsumerConfig
import net.corda.messagebus.api.configuration.ProducerConfig
import net.corda.messagebus.api.constants.ConsumerRoles
import net.corda.messagebus.api.constants.ProducerRoles
import net.corda.messagebus.kafka.config.ConfigResolver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConfigResolverTest {

    companion object {
        private const val TEST_CONFIG = "test.conf"

        private const val GROUP_NAME = "group"
        private const val CLIENT_ID = "test-client-id"
        private const val TOPIC_PREFIX = "topic-prefix"
        private const val INSTANCE_ID = 1

        private const val GROUP_ID_PROP = "group.id"
        private const val CLIENT_ID_PROP = "client.id"
        private const val ISOLATION_LEVEL_PROP = "isolation.level"
        private const val BOOTSTRAP_SERVERS_PROP = "bootstrap.servers"
        private const val SSL_KEYSTORE_PROP = "ssl.keystore.location"
        private const val SESSION_TIMEOUT_PROP = "session.timeout.ms"
        private const val AUTO_OFFSET_RESET_PROP = "auto.offset.reset"
        private const val TRANSACTIONAL_ID_PROP = "transactional.id"
        private const val ACKS_PROP = "acks"
    }

    private val smartConfigFactory = SmartConfigFactory.create(ConfigFactory.empty())

    @Test
    fun `config resolution for pubsub consumer`() {
        val busConfig = loadTestConfig()
        val resolver = ConfigResolver(smartConfigFactory)
        val properties = resolver.resolve(
            busConfig,
            ConsumerConfig(GROUP_NAME, CLIENT_ID, TOPIC_PREFIX, ConsumerRoles.PUBSUB)
        )
        // Verify substitutions
        assertEquals(GROUP_NAME, properties[GROUP_ID_PROP])
        assertEquals("$GROUP_NAME-consumer-$CLIENT_ID", properties[CLIENT_ID_PROP])
        assertEquals("read_committed", properties[ISOLATION_LEVEL_PROP]) // Verify an enforced property
        assertEquals("kafka:1001", properties[BOOTSTRAP_SERVERS_PROP]) // Verify overriding a default
        assertEquals("foo/bar", properties[SSL_KEYSTORE_PROP]) // Verify providing a property that has no default
        assertEquals("6000", properties[SESSION_TIMEOUT_PROP]) // Verify a property left at default
        assertEquals("latest", properties[AUTO_OFFSET_RESET_PROP]) // Verify Pubsub specific override
    }

    @Test
    fun `config resolution for compacted consumer`() {
        val busConfig = loadTestConfig()
        val resolver = ConfigResolver(smartConfigFactory)
        val properties = resolver.resolve(
            busConfig,
            ConsumerConfig(GROUP_NAME, CLIENT_ID, TOPIC_PREFIX, ConsumerRoles.COMPACTED)
        )
        // Verify substitutions
        assertEquals(GROUP_NAME, properties[GROUP_ID_PROP])
        assertEquals("$GROUP_NAME-consumer-$CLIENT_ID", properties[CLIENT_ID_PROP])
        assertEquals("read_committed", properties[ISOLATION_LEVEL_PROP]) // Verify an enforced property
        assertEquals("kafka:1001", properties[BOOTSTRAP_SERVERS_PROP]) // Verify overriding a default
        assertEquals("foo/bar", properties[SSL_KEYSTORE_PROP]) // Verify providing a property that has no default
        assertEquals("6000", properties[SESSION_TIMEOUT_PROP]) // Verify a property left at default
        assertEquals("earliest", properties[AUTO_OFFSET_RESET_PROP]) // Verify specific overrides do not occur
    }

    @Test
    fun `config resolution for producer for standard publisher`() {
        val busConfig = loadTestConfig()
        val resolver = ConfigResolver(smartConfigFactory)
        val properties =
            resolver.resolve(busConfig, ProducerConfig(CLIENT_ID, INSTANCE_ID, TOPIC_PREFIX, ProducerRoles.PUBLISHER))
        // Verify substitutions
        assertEquals("$CLIENT_ID-producer", properties[CLIENT_ID_PROP])
        assertEquals("$CLIENT_ID-$INSTANCE_ID", properties[TRANSACTIONAL_ID_PROP])
        assertEquals("all", properties[ACKS_PROP])
        assertEquals("kafka:1001", properties[BOOTSTRAP_SERVERS_PROP]) // Verify overriding a default
        assertEquals("foo/bar", properties[SSL_KEYSTORE_PROP]) // Verify providing a property that has no default
    }

    @Test
    fun `no transactional id is present if the instance id is not set`() {
        val busConfig = loadTestConfig()
        val resolver = ConfigResolver(smartConfigFactory)
        val properties =
            resolver.resolve(busConfig, ProducerConfig(CLIENT_ID, null, TOPIC_PREFIX, ProducerRoles.PUBLISHER))
        println(properties)
        // Verify substitutions
        assertEquals("$CLIENT_ID-producer", properties[CLIENT_ID_PROP])
        assertEquals(null, properties[TRANSACTIONAL_ID_PROP])
        assertEquals("all", properties[ACKS_PROP])
        assertEquals("kafka:1001", properties[BOOTSTRAP_SERVERS_PROP]) // Verify overriding a default
        assertEquals("foo/bar", properties[SSL_KEYSTORE_PROP]) // Verify providing a property that has no default
    }

    private fun loadTestConfig(): SmartConfig {
        val url = this::class.java.classLoader.getResource(TEST_CONFIG)
            ?: throw IllegalArgumentException("Failed to find $TEST_CONFIG")
        val configString = url.openStream().bufferedReader().use {
            it.readText()
        }
        return smartConfigFactory.create(ConfigFactory.parseString(configString))
    }
}