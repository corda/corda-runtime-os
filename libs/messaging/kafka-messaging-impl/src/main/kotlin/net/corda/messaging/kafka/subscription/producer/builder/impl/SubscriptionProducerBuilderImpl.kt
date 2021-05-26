package net.corda.messaging.kafka.subscription.producer.builder.impl

import com.typesafe.config.Config
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.kafka.properties.PublisherConfigProperties
import net.corda.messaging.kafka.publisher.CordaAvroSerializer
import net.corda.messaging.kafka.subscription.producer.builder.SubscriptionProducerBuilder
import net.corda.messaging.kafka.subscription.producer.wrapper.CordaKafkaProducer
import net.corda.messaging.kafka.subscription.producer.wrapper.impl.CordaKafkaProducerImpl
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.internal.uncheckedCast
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.errors.AuthorizationException
import org.apache.kafka.common.errors.InterruptException
import org.apache.kafka.common.errors.TimeoutException
import org.apache.kafka.common.errors.UnsupportedVersionException
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.Properties

/**
 * Generate a subscription producer builder applying the [config] and [producerProperties] to each new instance of a producer.
 * Use the [avroSchemaRegistry] SerDe for converting [Record] to kafka [ProducerRecord].
 * Initialise producer for transactions.
 */
class SubscriptionProducerBuilderImpl (
    private val config: Config,
    private val avroSchemaRegistry: AvroSchemaRegistry,
    private val producerProperties: Properties) : SubscriptionProducerBuilder {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    private val clientId = config.getString(PublisherConfigProperties.PUBLISHER_CLIENT_ID)

    override fun createProducer(consumer: Consumer<*, *>): CordaKafkaProducer {
        val contextClassLoader = Thread.currentThread().contextClassLoader
        val producer = try {
            Thread.currentThread().contextClassLoader = null;
            KafkaProducer<Any, Any>(producerProperties,
                uncheckedCast(StringSerializer()),
                CordaAvroSerializer(avroSchemaRegistry))
        } catch (ex: KafkaException) {
            val message =  "SubscriptionSubscriptionProducerBuilderImpl failed to producer with clientId $clientId."
            log.error(message, ex)
            throw CordaMessageAPIFatalException(message, ex)
        } finally {
            Thread.currentThread().contextClassLoader = contextClassLoader
        }

        initTransactionForProducer(producer)

        return CordaKafkaProducerImpl(config, producer, consumer)
    }

    /**
     * Initialise transactions for the producer.
     * @param producer to initialise transactions.
     * @throws CordaMessageAPIFatalException fatal error occurred.
     * @throws CordaMessageAPIIntermittentException error occurred that can be retried.
     */
    @Suppress("TooGenericExceptionCaught", "ThrowsCount")
    private fun initTransactionForProducer(producer: KafkaProducer<Any, Any>) {
        try {
            producer.initTransactions()
        } catch (ex: Exception) {
            val message = "Failed to initialize producer with " +
                    "transactionId ${producerProperties[ProducerConfig.TRANSACTIONAL_ID_CONFIG]} for transactions"
            when (ex) {
                is IllegalStateException,
                is UnsupportedVersionException,
                is AuthorizationException -> {
                    throw CordaMessageAPIFatalException(message, ex)
                }
                is KafkaException,
                is InterruptException,
                is TimeoutException -> {
                    throw CordaMessageAPIIntermittentException(message, ex)
                }
                else -> {
                    throw CordaMessageAPIFatalException("$message. Unexpected error occurred.", ex)
                }
            }
        }
    }
}
