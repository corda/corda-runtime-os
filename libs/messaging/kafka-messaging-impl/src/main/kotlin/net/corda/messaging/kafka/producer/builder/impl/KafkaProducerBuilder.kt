package net.corda.messaging.kafka.producer.builder.impl

import com.typesafe.config.Config
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.kafka.producer.builder.ProducerBuilder
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.KAFKA_CLOSE_TIMEOUT
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
 * Builder for a Kafka Producer. Initialises producer for transactions.
 * If exceptions are thrown in the construction of a KafKaProducer then it is closed and returned as null and exception is logged.
 */
class KafkaProducerBuilder<K, V> : ProducerBuilder<K, V> {

    private companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }
    
    override fun createProducer(defaultKafkaConfig: Config,
                                producerProperties: Properties,
                                publisherConfig: PublisherConfig): Producer<K, V>? {
        var producer: Producer<K, V>? = null
        var producerInitialized = false
        val timeout = defaultKafkaConfig.getLong(KAFKA_CLOSE_TIMEOUT)
        try {
            producer = KafkaProducer(producerProperties)
            producerInitialized = true
        } catch (ex: KafkaException) {
            log.error("Failed to create kafka producer clientId ${publisherConfig.clientId}, instanceId ${publisherConfig.instanceId}, " +
                    "topic ${publisherConfig.topic}.", ex)
        }

        if (producerInitialized) {
            try {
                producer?.initTransactions()
            } catch (ex: IllegalStateException ) {
                log.error("Failed to initialize kafka producer. No transactional.id has been configured. " +
                        "clientId ${publisherConfig.clientId}, instanceId ${publisherConfig.instanceId}, " +
                        "topic ${publisherConfig.topic}.", ex)
                producer!!.close(Duration.ofMillis(timeout))
                producer = null
            } catch (ex: UnsupportedVersionException) {
                log.error("Failed to initialize kafka producer. Broker does not support transactions. " +
                        "clientId ${publisherConfig.clientId}, instanceId ${publisherConfig.instanceId}, " +
                        "topic ${publisherConfig.topic}.", ex)
                producer!!.close(Duration.ofMillis(timeout))
                producer = null
            } catch (ex: AuthorizationException) {
                log.error("Failed to initialize kafka producer. Configured transactional.id is not authorized. " +
                        "clientId ${publisherConfig.clientId}, instanceId ${publisherConfig.instanceId}, " +
                        "topic ${publisherConfig.topic}.", ex)
                producer!!.close(Duration.ofMillis(timeout))
                producer = null
            } catch (ex: KafkaException) {
                log.error("Failed to initialize kafka producer. Fatal error encountered. " +
                        "clientId ${publisherConfig.clientId}, instanceId ${publisherConfig.instanceId}, " +
                        "topic ${publisherConfig.topic}.", ex)
                producer!!.close(Duration.ofMillis(timeout))
                producer = null
            } catch (ex: TimeoutException) {
                log.error("Failed to initialize kafka producer. Timeout. " +
                        "clientId ${publisherConfig.clientId}, instanceId ${publisherConfig.instanceId}, " +
                        "topic ${publisherConfig.topic}.", ex)
                producer!!.close(Duration.ofMillis(timeout))
                producer = null
            } catch (ex: InterruptException) {
                log.error("Failed to initialize kafka producer. Thread is interrupted while blocked. " +
                        "clientId ${publisherConfig.clientId}, instanceId ${publisherConfig.instanceId}, " +
                        "topic ${publisherConfig.topic}.", ex)
                producer!!.close(Duration.ofMillis(timeout))
                producer = null
            }
        }

        return producer
    }
}
