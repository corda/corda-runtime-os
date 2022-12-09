package net.corda.messagebus.kafka.config

import com.typesafe.config.ConfigFactory
import java.util.Properties
import java.util.stream.Stream
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.messagebus.api.configuration.ConsumerConfig
import net.corda.messagebus.api.configuration.ProducerConfig
import net.corda.messagebus.api.constants.ConsumerRoles
import net.corda.messagebus.api.constants.ProducerRoles
import net.corda.messaging.api.exception.CordaMessageAPIConfigException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class MessageBusConfigResolverTest {

    companion object {
        private const val TEST_CONFIG = "test.conf"
        private const val EMPTY_CONFIG = "empty.conf"
        private const val INCORRECT_BUS_CONFIG = "incorrect-bus.conf"

        private const val GROUP_NAME = "group"
        private const val CLIENT_ID = "test-client-id"
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

        @JvmStatic
        @Suppress("unused", "LongMethod")
        private fun consumerConfigSource(): Stream<Arguments> {
            val arguments = mapOf(
                ConsumerRoles.PUBSUB to getExpectedConsumerProperties(
                    mapOf(
                        BOOTSTRAP_SERVERS_PROP to "kafka:1001",
                        SSL_KEYSTORE_PROP to "foo/bar",
                        AUTO_OFFSET_RESET_PROP to "latest"
                    )
                ),
                ConsumerRoles.COMPACTED to getExpectedConsumerProperties(
                    mapOf(
                        BOOTSTRAP_SERVERS_PROP to "kafka:1001",
                        SSL_KEYSTORE_PROP to "foo/bar",
                    )
                ),
                ConsumerRoles.DURABLE to getExpectedConsumerProperties(
                    mapOf(
                        BOOTSTRAP_SERVERS_PROP to "kafka:1001",
                        SSL_KEYSTORE_PROP to "foo/bar",
                    )
                ),
                ConsumerRoles.SAE_STATE to getExpectedConsumerProperties(
                    mapOf(
                        BOOTSTRAP_SERVERS_PROP to "kafka:1001",
                        SSL_KEYSTORE_PROP to "foo/bar",
                        CLIENT_ID_PROP to "$GROUP_NAME-stateConsumer-$CLIENT_ID"
                    )
                ),
                ConsumerRoles.SAE_EVENT to getExpectedConsumerProperties(
                    mapOf(
                        BOOTSTRAP_SERVERS_PROP to "kafka:1001",
                        SSL_KEYSTORE_PROP to "foo/bar",
                        CLIENT_ID_PROP to "$GROUP_NAME-eventConsumer-$CLIENT_ID"
                    )
                ),
                ConsumerRoles.EVENT_LOG to getExpectedConsumerProperties(
                    mapOf(
                        BOOTSTRAP_SERVERS_PROP to "kafka:1001",
                        SSL_KEYSTORE_PROP to "foo/bar",
                    )
                ),
                ConsumerRoles.RPC_SENDER to getExpectedConsumerProperties(
                    mapOf(
                        GROUP_ID_PROP to "$GROUP_NAME-sender",
                        BOOTSTRAP_SERVERS_PROP to "kafka:1001",
                        SSL_KEYSTORE_PROP to "foo/bar",
                        AUTO_OFFSET_RESET_PROP to "latest"
                    )
                ),
                ConsumerRoles.RPC_RESPONDER to getExpectedConsumerProperties(
                    mapOf(
                        GROUP_ID_PROP to "$GROUP_NAME-responder",
                        BOOTSTRAP_SERVERS_PROP to "kafka:1001",
                        SSL_KEYSTORE_PROP to "foo/bar",
                        AUTO_OFFSET_RESET_PROP to "latest"
                    )
                )
            )
            return arguments.entries.stream().map { (role, properties) -> Arguments.arguments(role, properties) }
        }

        @JvmStatic
        @Suppress("unused", "LongMethod")
        private fun producerConfigSource(): Stream<Arguments> {
            val arguments = mapOf(
                ProducerRoles.PUBLISHER to getExpectedProducerProperties(
                    mapOf(
                        BOOTSTRAP_SERVERS_PROP to "kafka:1001",
                        SSL_KEYSTORE_PROP to "foo/bar"
                    )
                ),
                ProducerRoles.DURABLE to getExpectedProducerProperties(
                    mapOf(
                        BOOTSTRAP_SERVERS_PROP to "kafka:1001",
                        SSL_KEYSTORE_PROP to "foo/bar"
                    )
                ),
                ProducerRoles.SAE_PRODUCER to getExpectedProducerProperties(
                    mapOf(
                        BOOTSTRAP_SERVERS_PROP to "kafka:1001",
                        SSL_KEYSTORE_PROP to "foo/bar"
                    )
                ),
                ProducerRoles.EVENT_LOG to getExpectedProducerProperties(
                    mapOf(
                        BOOTSTRAP_SERVERS_PROP to "kafka:1001",
                        SSL_KEYSTORE_PROP to "foo/bar"
                    )
                ),
                ProducerRoles.RPC_SENDER to getExpectedProducerProperties(
                    mapOf(
                        BOOTSTRAP_SERVERS_PROP to "kafka:1001",
                        SSL_KEYSTORE_PROP to "foo/bar"
                    )
                ),
                ProducerRoles.RPC_RESPONDER to getExpectedProducerProperties(
                    mapOf(
                        BOOTSTRAP_SERVERS_PROP to "kafka:1001",
                        SSL_KEYSTORE_PROP to "foo/bar"
                    )
                )
            )
            return arguments.entries.stream().map { (role, properties) -> Arguments.arguments(role, properties) }
        }

        private fun getExpectedConsumerProperties(overrides: Map<String, String?>): Properties {
            val defaults = mapOf(
                GROUP_ID_PROP to GROUP_NAME,
                CLIENT_ID_PROP to "$GROUP_NAME-consumer-$CLIENT_ID",
                ISOLATION_LEVEL_PROP to "read_committed",
                BOOTSTRAP_SERVERS_PROP to "localhost:9092",
                SESSION_TIMEOUT_PROP to "6000",
                AUTO_OFFSET_RESET_PROP to "earliest"
            )
            val properties = Properties()
            for ((key, value) in defaults) {
                properties.setProperty(key, value)
            }
            for ((key, value) in overrides) {
                properties.setProperty(key, value)
            }
            return properties
        }

        private fun getExpectedProducerProperties(overrides: Map<String, String?>): Properties {
            val defaults = mapOf(
                GROUP_ID_PROP to GROUP_NAME,
                CLIENT_ID_PROP to "$CLIENT_ID-producer",
                TRANSACTIONAL_ID_PROP to "$CLIENT_ID-$INSTANCE_ID",
                BOOTSTRAP_SERVERS_PROP to "localhost:9092",
                ACKS_PROP to "all"
            )
            val properties = Properties()
            for ((key, value) in defaults) {
                properties.setProperty(key, value)
            }
            for ((key, value) in overrides) {
                if (value != null) {
                    properties.setProperty(key, value)
                } else {
                    properties.remove(key)
                }
            }
            return properties
        }
    }

    private val smartConfigFactory = SmartConfigFactory.create(ConfigFactory.empty())

    @ParameterizedTest(name = "Config resolution for consumers: {0}")
    @MethodSource("consumerConfigSource")
    fun `config resolution for consumers`(role: ConsumerRoles, expectedProperties: Properties) {
        val messageBusConfig = loadTestConfig(TEST_CONFIG)
        val resolver = MessageBusConfigResolver(smartConfigFactory)
        val (_, properties) = resolver.resolve(
            messageBusConfig,
            ConsumerConfig(GROUP_NAME, CLIENT_ID, role)
        )
        assertConsumerProperties(expectedProperties, properties)
    }

    @Test
    fun `an empty config can be resolved correctly for consumers`() {
        val messageBusConfig = loadTestConfig(EMPTY_CONFIG)
        val resolver = MessageBusConfigResolver(smartConfigFactory)
        val (_, properties) = resolver.resolve(
            messageBusConfig,
            ConsumerConfig(GROUP_NAME, CLIENT_ID, ConsumerRoles.COMPACTED)
        )
        val expectedProperties = getExpectedConsumerProperties(mapOf())
        // Verify substitutions
        assertConsumerProperties(expectedProperties, properties)
    }

    @ParameterizedTest(name = "Config resolution for producers: {0}")
    @MethodSource("producerConfigSource")
    fun `config resolution for producers`(role: ProducerRoles, expectedProperties: Properties) {
        val messageBusConfig = loadTestConfig(TEST_CONFIG)
        val resolver = MessageBusConfigResolver(smartConfigFactory)
        val (_, properties) =
            resolver.resolve(messageBusConfig, ProducerConfig(CLIENT_ID, INSTANCE_ID, true, role))
        assertProducerProperties(expectedProperties, properties)
    }

    @Test
    fun `an empty config can be resolved for producers`() {
        val messageBusConfig = loadTestConfig(EMPTY_CONFIG)
        val resolver = MessageBusConfigResolver(smartConfigFactory)
        val (_, properties) =
            resolver.resolve(
                messageBusConfig,
                ProducerConfig(CLIENT_ID, INSTANCE_ID, true, ProducerRoles.RPC_RESPONDER)
            )
        val expectedProperties = getExpectedProducerProperties(mapOf())
        // Verify substitutions
        assertProducerProperties(expectedProperties, properties)
    }

    @Test
    fun `no transactional id is present if transactional set to false`() {
        val messageBusConfig = loadTestConfig(TEST_CONFIG)
        val resolver = MessageBusConfigResolver(smartConfigFactory)
        val (_, properties) =
            resolver.resolve(messageBusConfig, ProducerConfig(CLIENT_ID, 1, false, ProducerRoles.PUBLISHER))
        val expectedProperties = getExpectedProducerProperties(
            mapOf(
                TRANSACTIONAL_ID_PROP to null,
                BOOTSTRAP_SERVERS_PROP to "kafka:1001",
                SSL_KEYSTORE_PROP to "foo/bar"
            )
        )
        assertProducerProperties(expectedProperties, properties)
    }

    @Test
    fun `configuration resolution fails if the bus is the incorrect type`() {
        val messageBusConfig = loadTestConfig(INCORRECT_BUS_CONFIG)
        val resolver = MessageBusConfigResolver(smartConfigFactory)
        assertThrows<CordaMessageAPIConfigException> {
            resolver.resolve(messageBusConfig, ConsumerConfig(GROUP_NAME, CLIENT_ID, ConsumerRoles.DURABLE))
        }
        assertThrows<CordaMessageAPIConfigException> {
            resolver.resolve(messageBusConfig, ProducerConfig(CLIENT_ID, 1, true, ProducerRoles.DURABLE))
        }
    }

    private fun loadTestConfig(resource: String): SmartConfig {
        val url = this::class.java.classLoader.getResource(resource)
            ?: throw IllegalArgumentException("Failed to find $resource")
        val configString = url.openStream().bufferedReader().use {
            it.readText()
        }
        return smartConfigFactory.create(ConfigFactory.parseString(configString))
    }

    // For assertions, the actual properties can contain some superfluous stuff relating to producers, as the common
    // configuration block doesn't ensure that only shared properties are placed in there. This doesn't matter from
    // Kafka's perspective, so we just assert on the properties that do matter here.
    private fun assertConsumerProperties(expected: Properties, actual: Properties) {
        assertEquals(expected[GROUP_ID_PROP], actual[GROUP_ID_PROP])
        assertEquals(expected[CLIENT_ID_PROP], actual[CLIENT_ID_PROP])
        assertEquals(expected[ISOLATION_LEVEL_PROP], actual[ISOLATION_LEVEL_PROP]) // Verify an enforced property
        assertEquals(expected[BOOTSTRAP_SERVERS_PROP], actual[BOOTSTRAP_SERVERS_PROP]) // Verify overriding a default
        assertEquals(
            expected[SSL_KEYSTORE_PROP],
            actual[SSL_KEYSTORE_PROP]
        ) // Verify providing a property that has no default
        assertEquals(expected[SESSION_TIMEOUT_PROP], actual[SESSION_TIMEOUT_PROP]) // Verify a property left at default
        assertEquals(
            expected[AUTO_OFFSET_RESET_PROP],
            actual[AUTO_OFFSET_RESET_PROP]
        ) // Verify Pubsub specific override
    }

    private fun assertProducerProperties(expected: Properties, actual: Properties) {
        assertEquals(expected[CLIENT_ID_PROP], actual[CLIENT_ID_PROP])
        assertEquals(expected[TRANSACTIONAL_ID_PROP], actual[TRANSACTIONAL_ID_PROP])
        assertEquals(expected[ACKS_PROP], actual[ACKS_PROP])
        assertEquals(expected[BOOTSTRAP_SERVERS_PROP], actual[BOOTSTRAP_SERVERS_PROP])
        assertEquals(expected[SSL_KEYSTORE_PROP], actual[SSL_KEYSTORE_PROP])
    }
}