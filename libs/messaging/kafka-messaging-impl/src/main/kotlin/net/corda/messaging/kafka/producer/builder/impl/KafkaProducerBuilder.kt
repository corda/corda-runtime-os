package net.corda.messaging.kafka.producer.builder.impl

import com.typesafe.config.Config
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.kafka.producer.builder.ProducerBuilder
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.PRODUCER_CLOSE_TIMEOUT
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.PRODUCER_CREATE_MAX_RETRIES
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.errors.AuthorizationException
import org.apache.kafka.common.errors.InterruptException
import org.apache.kafka.common.errors.TimeoutException
import org.apache.kafka.common.errors.UnsupportedVersionException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.Properties

/**
 * Builder for a Kafka Producer. Initialises producer for transactions if publisherConfig contains an instanceId.
 * If fatal exception is thrown in the construction of a KafKaProducer
 * then it is closed and exception is thrown as [CordaMessageAPIFatalException].
 */
class KafkaProducerBuilder<K, V> : ProducerBuilder<K, V> {

    private companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }
    
    override fun createProducer(config: Config,
                                properties: Properties,
                                publisherConfig: PublisherConfig): Producer<K, V> {
        var producer: Producer<K, V>? = null
        val producerCloseTimeout = config.getLong(PRODUCER_CLOSE_TIMEOUT)
        val producerCreateMaxRetries = config.getLong(PRODUCER_CREATE_MAX_RETRIES)

        try {
            producer = KafkaProducer(properties)
        } catch (ex: KafkaException) {
            log.error("Failed to create kafka producer clientId ${publisherConfig.clientId}, instanceId ${publisherConfig.instanceId}, " +
                    "topic ${publisherConfig.topic}.", ex)
            safeClose(producer!!, producerCloseTimeout)
            throw CordaMessageAPIFatalException(
                "Failed to create kafka producer.",
                ex
            )
        }

        if (publisherConfig.instanceId != null) {
            initTransactionForProducer(0, publisherConfig, producer, producerCloseTimeout, producerCreateMaxRetries)
        }

        return producer
    }

    /**
     * Initialise transactions for the transactional producer. Attempt to retry any [InterruptException] or [TimeoutException]
     * @param currentAttempts current amount of attempts to execute initialisation, 0 for first attempt
     * @param publisherConfig required for logging information
     * @param producer to initialise transactions for
     * @param producerCloseTimeout time to wait for close to finish
     * @param producerCreateMaxRetries maximum amount of retries to attempt
     */
    private fun initTransactionForProducer(currentAttempts: Int,
                                        publisherConfig: PublisherConfig,
                                        producer: KafkaProducer<K, V>,
                                        producerCloseTimeout: Long,
                                        producerCreateMaxRetries: Long) {
        var attempts = currentAttempts
        try {
            producer.initTransactions()
        } catch (ex: IllegalStateException) {
            val message = "Failed to initialize kafka producer. No transactional.id has been configured. " +
                    "clientId ${publisherConfig.clientId}, instanceId ${publisherConfig.instanceId}, " +
                    "topic ${publisherConfig.topic}."
            logErrorAndCloseProducer(message, ex, producer, producerCloseTimeout)
        } catch (ex: UnsupportedVersionException) {
            val message = "Failed to initialize kafka producer. Broker does not support transactions. " +
                    "clientId ${publisherConfig.clientId}, instanceId ${publisherConfig.instanceId}, " +
                    "topic ${publisherConfig.topic}."
            logErrorAndCloseProducer(message, ex, producer, producerCloseTimeout)
        } catch (ex: AuthorizationException) {
            val message = "Failed to initialize kafka producer. Configured transactional.id is not authorized. " +
                    "clientId ${publisherConfig.clientId}, instanceId ${publisherConfig.instanceId}, " +
                    "topic ${publisherConfig.topic}."
            logErrorAndCloseProducer(message, ex, producer, producerCloseTimeout)
        } catch (ex: KafkaException) {
            val message = "Failed to initialize kafka producer. Fatal error encountered. " +
                    "clientId ${publisherConfig.clientId}, instanceId ${publisherConfig.instanceId}, " +
                    "topic ${publisherConfig.topic}."
            logErrorAndCloseProducer(message, ex, producer, producerCloseTimeout)
        } catch (ex: TimeoutException) {
            attempts++
            if (attempts < producerCreateMaxRetries) {
                val message = "Failed to initialize kafka producer. Timeout. " +
                        "clientId ${publisherConfig.clientId}, instanceId ${publisherConfig.instanceId}, " +
                        "topic ${publisherConfig.topic}. Retrying."
                log.error(message, ex)
                initTransactionForProducer(attempts, publisherConfig, producer, producerCloseTimeout, producerCreateMaxRetries)
            } else {
                val message = "Failed to initialize kafka producer. Timeout. " +
                        "clientId ${publisherConfig.clientId}, instanceId ${publisherConfig.instanceId}, " +
                        "topic ${publisherConfig.topic}."
                logErrorAndCloseProducer(message, ex, producer, producerCloseTimeout)
            }
        } catch (ex: InterruptException) {
            attempts++
            if (attempts < producerCreateMaxRetries) {
                val message = "Failed to initialize kafka producer. Thread is interrupted while blocked. " +
                        "clientId ${publisherConfig.clientId}, instanceId ${publisherConfig.instanceId}, " +
                        "topic ${publisherConfig.topic}. Retrying."
                log.error(message, ex)
                initTransactionForProducer(attempts, publisherConfig, producer, producerCloseTimeout, producerCreateMaxRetries)
            } else {
                val message = "Failed to initialize kafka producer. Thread is interrupted while blocked. " +
                        "clientId ${publisherConfig.clientId}, instanceId ${publisherConfig.instanceId}, " +
                        "topic ${publisherConfig.topic}."
                logErrorAndCloseProducer(message, ex, producer, producerCloseTimeout)
            }
        }
    }

    /**
     * Log an error [message] and throw an [exception] as a [CordaMessageAPIFatalException].
     * Close [producer] with the set [timeout].
     */
    private fun logErrorAndCloseProducer(message: String, exception: Exception, producer: KafkaProducer<K, V>, timeout: Long) {
        log.error(message)
        safeClose(producer, timeout)
        throw CordaMessageAPIFatalException(message,exception)
    }

    /**
     * Safely close [producer] and swallow any exceptions thrown.
     * @param producer kafka producer.
     * @param timeoutMillis time to wait for close to finish.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun safeClose(producer: Producer<K, V>, timeoutMillis: Long) {
        try {
            producer.close(Duration.ofMillis(timeoutMillis))
        } catch (ex: Exception) {
            log.error("Failed to close producer safely.", ex)
        }
    }
}
