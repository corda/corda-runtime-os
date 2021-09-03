package net.corda.messaging.kafka.producer.builder.impl

import com.typesafe.config.Config
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.kafka.producer.builder.ProducerBuilder
import net.corda.messaging.kafka.producer.wrapper.CordaKafkaProducer
import net.corda.messaging.kafka.producer.wrapper.impl.CordaKafkaProducerImpl
import net.corda.messaging.kafka.publisher.CordaAvroSerializer
import net.corda.messaging.kafka.utils.toProperties
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.util.contextLogger
import org.apache.kafka.clients.CommonClientConfigs.CLIENT_ID_CONFIG
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.KafkaException
import org.slf4j.Logger

/**
 * Builder for a Kafka Producer.
 * Initialises producer for transactions if publisherConfig contains an instanceId.
 * Producer uses avro for serialization.
 * If fatal exception is thrown in the construction of a KafKaProducer
 * then it is closed and exception is thrown as [CordaMessageAPIFatalException].
 */
class KafkaProducerBuilderImpl(
    private val avroSchemaRegistry: AvroSchemaRegistry
) : ProducerBuilder {

    companion object {
        private val log: Logger = contextLogger()
    }

    override fun createProducer(producerConfig: Config): CordaKafkaProducer {
        val contextClassLoader = Thread.currentThread().contextClassLoader
        val producer = try {
            Thread.currentThread().contextClassLoader = null;
            KafkaProducer(
                producerConfig.toProperties(),
                CordaAvroSerializer(avroSchemaRegistry),
                CordaAvroSerializer(avroSchemaRegistry)
            )
        } catch (ex: KafkaException) {
            val clientId = producerConfig.getString(CLIENT_ID_CONFIG)
            val message = "SubscriptionSubscriptionProducerBuilderImpl failed to producer with clientId $clientId."
            log.error(message, ex)
            throw CordaMessageAPIFatalException(message, ex)
        } finally {
            Thread.currentThread().contextClassLoader = contextClassLoader
        }

        return CordaKafkaProducerImpl(producerConfig, producer)
    }
}
