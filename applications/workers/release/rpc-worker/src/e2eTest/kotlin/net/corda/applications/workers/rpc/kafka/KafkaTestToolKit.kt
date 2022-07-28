package net.corda.applications.workers.rpc.kafka

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.impl.LifecycleCoordinatorFactoryImpl
import net.corda.lifecycle.impl.LifecycleCoordinatorSchedulerFactoryImpl
import net.corda.lifecycle.impl.registry.LifecycleRegistryImpl
import net.corda.messagebus.kafka.consumer.builder.CordaKafkaConsumerBuilderImpl
import net.corda.messagebus.kafka.producer.builder.KafkaCordaProducerBuilderImpl
import net.corda.messagebus.kafka.serialization.CordaAvroSerializationFactoryImpl
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.publisher.factory.CordaPublisherFactory
import net.corda.messaging.subscription.consumer.builder.StateAndEventBuilderImpl
import net.corda.messaging.subscription.factory.CordaSubscriptionFactory
import net.corda.schema.configuration.BootConfig
import net.corda.schema.configuration.BootConfig.BOOT_KAFKA_COMMON
import net.corda.schema.configuration.MessagingConfig
import net.corda.schema.registry.impl.AvroSchemaRegistryImpl
import kotlin.random.Random

internal class KafkaTestToolKit {
    private val kafkaHost = System.getenv("KAFKA-HOST-NAME") ?: "localhost"
    private val kafkaPort = (System.getenv("KAFKA-PORT") ?: "9092").toIntOrNull() ?: 9092
    private val registry by lazy {
        AvroSchemaRegistryImpl()
    }
    private val serializationFactory by lazy {
        CordaAvroSerializationFactoryImpl(registry)
    }
    private val consumerBuilder by lazy {
        CordaKafkaConsumerBuilderImpl(registry)
    }
    private val producerBuilder by lazy {
        KafkaCordaProducerBuilderImpl(registry)
    }
    private val coordinatorFactory by lazy {
        LifecycleCoordinatorFactoryImpl(
            LifecycleRegistryImpl(), LifecycleCoordinatorSchedulerFactoryImpl()
        )
    }
    private val stateAndEventBuilder by lazy {
        StateAndEventBuilderImpl(
            consumerBuilder,
            producerBuilder,
        )
    }

    val messagingConfiguration by lazy {
        val trustStoreLocation = if (System.getenv("KAFKA_TRUSTSTORE") != null) {
            mapOf(
                "ssl.truststore.location" to System.getenv("KAFKA_TRUSTSTORE"),
                "ssl.truststore.type" to "PEM",
            )
        } else {
            emptyMap()
        }
        val sslPassword = if (System.getenv("SASL_SECRET") != null) {
            mapOf(
                "security.protocol" to "SASL_SSL",
                "ssl.truststore.password" to System.getenv("SASL_SECRET"),
                "sasl.mechanism" to "SCRAM-SHA-256",
            )
        } else {
            emptyMap()
        }
        val bootstrapServers = mapOf(
            "bootstrap.servers" to "$kafkaHost:$kafkaPort"
        )
        val kafkaProperties = (trustStoreLocation + sslPassword + bootstrapServers).mapKeys { (key, _) ->
            "$BOOT_KAFKA_COMMON.${key.trim()}"
        }

        val secretsConfig = ConfigFactory.empty()
        SmartConfigFactory.create(secretsConfig).create(
            ConfigFactory.parseMap(kafkaProperties)
                .withValue(MessagingConfig.Bus.BUS_TYPE, ConfigValueFactory.fromAnyRef("KAFKA"))
                .withValue(MessagingConfig.Bus.KAFKA_PRODUCER_CLIENT_ID, ConfigValueFactory.fromAnyRef("e2e-test"))
                .withValue(BootConfig.TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(""))
                .withValue(BootConfig.INSTANCE_ID, ConfigValueFactory.fromAnyRef(Random.nextInt()))
        )
    }

    val subscriptionFactory: SubscriptionFactory by lazy {
        CordaSubscriptionFactory(
            serializationFactory,
            coordinatorFactory,
            producerBuilder,
            consumerBuilder,
            stateAndEventBuilder
        )
    }

    val publisherFactory: PublisherFactory by lazy {
        CordaPublisherFactory(
            serializationFactory,
            producerBuilder,
            consumerBuilder,
            coordinatorFactory
        )
    }
}
