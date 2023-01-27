package net.corda.applications.workers.rpc.kafka

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import net.corda.applications.workers.rpc.http.TestToolkit
import net.corda.chunking.impl.ChunkBuilderServiceImpl
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.lifecycle.impl.LifecycleCoordinatorFactoryImpl
import net.corda.lifecycle.impl.LifecycleCoordinatorSchedulerFactoryImpl
import net.corda.lifecycle.impl.registry.LifecycleRegistryImpl
import net.corda.messagebus.kafka.consumer.builder.CordaKafkaConsumerBuilderImpl
import net.corda.messagebus.kafka.producer.builder.KafkaCordaProducerBuilderImpl
import net.corda.messagebus.kafka.serialization.CordaAvroSerializationFactoryImpl
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.chunking.MessagingChunkFactoryImpl
import net.corda.messaging.publisher.factory.CordaPublisherFactory
import net.corda.messaging.subscription.consumer.builder.StateAndEventBuilderImpl
import net.corda.messaging.subscription.factory.CordaSubscriptionFactory
import net.corda.schema.configuration.BootConfig
import net.corda.schema.configuration.MessagingConfig
import net.corda.schema.configuration.MessagingConfig.Bus.KAFKA_PROPERTIES_COMMON
import net.corda.schema.registry.impl.AvroSchemaRegistryImpl

class KafkaTestToolKit(
    private val toolkit: TestToolkit,
) {
    private companion object {
        const val KAFKA_PROPERTY_PREFIX = "CORDA_KAFKA_"
    }

    private val registry by lazy {
        AvroSchemaRegistryImpl()
    }

    private val messageChunkFactory by lazy {
        MessagingChunkFactoryImpl(ChunkBuilderServiceImpl())
    }

    private val serializationFactory by lazy {
        CordaAvroSerializationFactoryImpl(registry)
    }
    private val consumerBuilder by lazy {
        CordaKafkaConsumerBuilderImpl(registry)
    }
    private val producerBuilder by lazy {
        KafkaCordaProducerBuilderImpl(registry, messageChunkFactory)
    }
    private val coordinatorFactory by lazy {
        LifecycleCoordinatorFactoryImpl(
            LifecycleRegistryImpl(),
            LifecycleCoordinatorSchedulerFactoryImpl()
        )
    }
    private val stateAndEventBuilder by lazy {
        StateAndEventBuilderImpl(
            consumerBuilder,
            producerBuilder
        )
    }

    val messagingConfiguration by lazy {
        val kafkaProperties = System.getenv().filterKeys {
            it.startsWith(KAFKA_PROPERTY_PREFIX)
        }.mapKeys { (key, _) ->
            key.removePrefix(KAFKA_PROPERTY_PREFIX)
        }.mapKeys { (key, _) ->
            key.lowercase().replace('_', '.')
        }.mapKeys { (key, _) ->
            "$KAFKA_PROPERTIES_COMMON.${key.trim()}"
        }

        SmartConfigFactory.createWithoutSecurityServices().create(
            ConfigFactory.parseMap(kafkaProperties)
                .withValue(MessagingConfig.Bus.BUS_TYPE, ConfigValueFactory.fromAnyRef("KAFKA"))
                .withValue(MessagingConfig.Bus.KAFKA_PRODUCER_CLIENT_ID, ConfigValueFactory.fromAnyRef("e2e-test"))
                .withValue(BootConfig.TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(""))
                .withValue(BootConfig.INSTANCE_ID, ConfigValueFactory.fromAnyRef(Random.nextInt().toString()))
        )
    }

    private val subscriptionFactory: SubscriptionFactory by lazy {
        CordaSubscriptionFactory(
            serializationFactory,
            coordinatorFactory,
            producerBuilder,
            consumerBuilder,
            stateAndEventBuilder
        )
    }

    private val publisherFactory: PublisherFactory by lazy {
        CordaPublisherFactory(
            serializationFactory,
            producerBuilder,
            consumerBuilder,
            coordinatorFactory
        )
    }

    /**
     * Creates easily attributable to a testcase unique name
     */
    val uniqueName: String get() = toolkit.uniqueName

    fun publishRecordsToKafka(records: Collection<Record<*, *>>) {
        if (records.isNotEmpty()) {
            publisherFactory.createPublisher(
                PublisherConfig(toolkit.uniqueName, false),
                messagingConfiguration,
            ).use { publisher ->
                publisher.start()
                publisher.publish(records.toList()).forEach {
                    it.get(30, TimeUnit.SECONDS)
                }
            }
        }
    }

    fun <K : Any, V : Any> acceptRecordsFromKafka(
        topic: String,
        keyClass: Class<K>,
        valueClass: Class<V>,
        block: (Record<K, V>) -> Unit,
    ): AutoCloseable {
        return subscriptionFactory.createDurableSubscription(
            subscriptionConfig = SubscriptionConfig(
                groupName = toolkit.uniqueName,
                eventTopic = topic,
            ),
            messagingConfig = messagingConfiguration,
            processor = object : DurableProcessor<K, V> {
                override fun onNext(events: List<Record<K, V>>): List<Record<*, *>> {
                    events.forEach(block)
                    return emptyList()
                }

                override val keyClass = keyClass
                override val valueClass = valueClass
            },
            partitionAssignmentListener = null,
        ).also {
            it.start()
        }
    }

    inline fun <reified K : Any, reified V : Any> acceptRecordsFromKafka(
        topic: String,
        noinline block: (Record<K, V>) -> Unit
    ): AutoCloseable {
        return acceptRecordsFromKafka(
            topic,
            K::class.java,
            V::class.java,
            block,
        )
    }
}
