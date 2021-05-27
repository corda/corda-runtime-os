package net.corda.messaging.kafka.producer.builder.impl

import com.typesafe.config.Config
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.kafka.producer.builder.ProducerBuilder
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.PRODUCER_CLOSE_TIMEOUT
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.PRODUCER_CREATE_MAX_RETRIES
import net.corda.messaging.kafka.properties.PublisherConfigProperties.Companion.PUBLISHER_CLIENT_ID
import net.corda.messaging.kafka.properties.PublisherConfigProperties.Companion.PUBLISHER_INSTANCE_ID
import net.corda.messaging.kafka.properties.PublisherConfigProperties.Companion.PUBLISHER_TOPIC
import net.corda.messaging.kafka.publisher.CordaAvroSerializer
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.internal.uncheckedCast
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.errors.AuthorizationException
import org.apache.kafka.common.errors.InterruptException
import org.apache.kafka.common.errors.TimeoutException
import org.apache.kafka.common.errors.UnsupportedVersionException
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*

/**
 * Builder for a Kafka Producer. Initialises producer for transactions if publisherConfig contains an instanceId.
 * If fatal exception is thrown in the construction of a KafKaProducer
 * then it is closed and exception is thrown as [CordaMessageAPIFatalException].
 */
class KafkaProducerBuilder<K : Any, V : Any>(
    private val avroSchemaRegistry: AvroSchemaRegistry,
) : ProducerBuilder<K, V> {

    private companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }
    
    override fun createProducer(config: Config, properties: Properties): Producer<K, V> {
        val producerCloseTimeout = config.getLong(PRODUCER_CLOSE_TIMEOUT)
        val producerCreateMaxRetries = config.getLong(PRODUCER_CREATE_MAX_RETRIES)
        val topic = config.getString(PUBLISHER_TOPIC)
        val clientId = config.getString(PUBLISHER_CLIENT_ID)
        val instanceId = if (config.hasPath(PUBLISHER_INSTANCE_ID)) config.getInt(PUBLISHER_INSTANCE_ID) else null

        val contextClassLoader = Thread.currentThread().contextClassLoader
        val producer = try {
            Thread.currentThread().contextClassLoader = null;
            KafkaProducer<K, V>(
                properties,
                uncheckedCast(StringSerializer()),
                CordaAvroSerializer<V>(avroSchemaRegistry),
            )
        } catch (ex: KafkaException) {
            log.error("Failed to create kafka producer clientId $clientId, instanceId $instanceId, " +
                    "topic $topic.", ex)
            throw CordaMessageAPIFatalException("Failed to create kafka producer.", ex)
        } finally {
            Thread.currentThread().contextClassLoader = contextClassLoader
        }

        if (instanceId != null) {
            initTransactionForProducer(config, producer, producerCloseTimeout, producerCreateMaxRetries)
        }

        return producer
    }

    /**
     * Initialise transactions for the transactional producer. Attempt to retry any [InterruptException] or [TimeoutException]
     * @param config required for logging information
     * @param producer to initialise transactions for
     * @param producerCloseTimeout time to wait for close to finish
     * @param producerCreateMaxRetries maximum amount of retries to attempt
     */
    private fun initTransactionForProducer(config: Config,
                                        producer: KafkaProducer<K, V>,
                                        producerCloseTimeout: Long,
                                        producerCreateMaxRetries: Long) {
        val topic = config.getString(PUBLISHER_TOPIC)
        val clientId = config.getString(PUBLISHER_CLIENT_ID)
        val instanceId  = config.getString(PUBLISHER_INSTANCE_ID)

        var attempts = 0
        while (true) {
            attempts++
            try {
                producer.initTransactions()
                break
            } catch (ex: IllegalStateException) {
                val message = "Failed to initialize kafka producer. No transactional.id has been configured. " +
                        "clientId $clientId, instanceId $instanceId, " +
                        "topic $topic."
                throwErrorAndCloseProducer(message, ex, producer, producerCloseTimeout)
            } catch (ex: UnsupportedVersionException) {
                val message = "Failed to initialize kafka producer. Broker does not support transactions. " +
                        "clientId $clientId, instanceId $instanceId, " +
                        "topic $topic."
                throwErrorAndCloseProducer(message, ex, producer, producerCloseTimeout)
            } catch (ex: AuthorizationException) {
                val message = "Failed to initialize kafka producer. Configured transactional.id is not authorized. " +
                        "clientId $clientId, instanceId $instanceId, " +
                        "topic $topic."
                throwErrorAndCloseProducer(message, ex, producer, producerCloseTimeout)
            } catch (ex: KafkaException) {
                val message = "Failed to initialize kafka producer. Fatal error encountered. " +
                        "clientId $clientId, instanceId $instanceId, " +
                        "topic $topic."
                throwErrorAndCloseProducer(message, ex, producer, producerCloseTimeout)
            } catch (ex: TimeoutException) {
                if (attempts <= producerCreateMaxRetries) {
                    val message = "Failed to initialize kafka producer. Timeout. " +
                            "clientId $clientId, instanceId $instanceId, " +
                            "topic $topic. Attempts: $attempts. Retrying."
                    log.warn(message, ex)
                } else {
                    val message = "Failed to initialize kafka producer. Timeout. " +
                            "clientId $clientId, instanceId $instanceId, " +
                            "topic $topic."
                    throwErrorAndCloseProducer(message, ex, producer, producerCloseTimeout)
                }
            } catch (ex: InterruptException) {
                if (attempts <= producerCreateMaxRetries) {
                    val message = "Failed to initialize kafka producer. Thread is interrupted while blocked. " +
                            "clientId $clientId, instanceId $instanceId, " +
                            "topic $topic. Attempts: $attempts. Retrying."
                    log.warn(message, ex)
                } else {
                    val message = "Failed to initialize kafka producer. Thread is interrupted while blocked. " +
                            "clientId $clientId, instanceId $instanceId, " +
                            "topic $topic."
                    throwErrorAndCloseProducer(message, ex, producer, producerCloseTimeout)
                }
            }
        }
    }

    /**
     * Log an error [message] and throw an [exception] as a [CordaMessageAPIFatalException].
     * Close [producer] with the set [timeout].
     */
    private fun throwErrorAndCloseProducer(message: String, exception: Exception, producer: KafkaProducer<K, V>, timeout: Long) {
        log.error(message)
        close(producer, timeout)
        throw CordaMessageAPIFatalException(message,exception)
    }

    /**
     * Safely close [producer] and swallow any exceptions thrown.
     * @param producer kafka producer.
     * @param timeoutMillis time to wait for close to finish.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun close(producer: Producer<K, V>, timeoutMillis: Long) {
        try {
            producer.close(Duration.ofMillis(timeoutMillis))
        } catch (ex: Exception) {
            log.error("Failed to close producer safely.", ex)
        }
    }
}
