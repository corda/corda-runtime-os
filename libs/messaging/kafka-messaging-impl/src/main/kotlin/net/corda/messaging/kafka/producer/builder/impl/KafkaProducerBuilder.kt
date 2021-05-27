package net.corda.messaging.kafka.producer.builder.impl

import com.typesafe.config.Config
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.kafka.producer.builder.ProducerBuilder
import net.corda.messaging.kafka.properties.PublisherConfigProperties.Companion.PUBLISHER_CLIENT_ID
import net.corda.messaging.kafka.publisher.CordaAvroSerializer
import net.corda.messaging.kafka.producer.wrapper.CordaKafkaProducer
import net.corda.messaging.kafka.producer.wrapper.impl.CordaKafkaProducerImpl
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.internal.uncheckedCast
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

/**
 * Builder for a Kafka Producer. Initialises producer for transactions if publisherConfig contains an instanceId.
 * If fatal exception is thrown in the construction of a KafKaProducer
 * then it is closed and exception is thrown as [CordaMessageAPIFatalException].
 */
class KafkaProducerBuilder(
    private val config: Config,
    private val avroSchemaRegistry: AvroSchemaRegistry,
    private val producerProperties: Properties
) : ProducerBuilder {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    private val clientId = config.getString(PUBLISHER_CLIENT_ID)

    override fun createProducer(): CordaKafkaProducer {
        val contextClassLoader = Thread.currentThread().contextClassLoader
        val producer = try {
            Thread.currentThread().contextClassLoader = null;
            KafkaProducer<Any, Any>(
                producerProperties,
                uncheckedCast(StringSerializer()),
                CordaAvroSerializer(avroSchemaRegistry)
            )
        } catch (ex: KafkaException) {
            val message = "SubscriptionSubscriptionProducerBuilderImpl failed to producer with clientId $clientId."
            log.error(message, ex)
            throw CordaMessageAPIFatalException(message, ex)
        } finally {
            Thread.currentThread().contextClassLoader = contextClassLoader
        }

        return CordaKafkaProducerImpl(config, producer)
    }
}
