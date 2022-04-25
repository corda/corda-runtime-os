package net.corda.messagebus.kafka.producer.builder

import net.corda.libs.configuration.SmartConfig
import net.corda.messagebus.api.configuration.ProducerConfig
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.builder.CordaProducerBuilder
import net.corda.messagebus.kafka.config.MessageBusConfigResolver
import net.corda.messagebus.kafka.producer.CordaKafkaProducerImpl
import net.corda.messagebus.kafka.serialization.CordaAvroSerializerImpl
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.util.contextLogger
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.KafkaException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import java.util.*

/**
 * Builder for a Kafka Producer.
 * Initialises producer for transactions if publisherConfig contains an instanceId.
 * Producer uses avro for serialization.
 * If fatal exception is thrown in the construction of a KafKaProducer
 * then it is closed and exception is thrown as [CordaMessageAPIFatalException].
 */
@Component(service = [CordaProducerBuilder::class])
class KafkaCordaProducerBuilderImpl @Activate constructor(
    @Reference(service = AvroSchemaRegistry::class)
    private val avroSchemaRegistry: AvroSchemaRegistry
) : CordaProducerBuilder {

    companion object {
        private val log: Logger = contextLogger()
    }

    override fun createProducer(producerConfig: ProducerConfig, messageBusConfig: SmartConfig): CordaProducer {
        val configResolver = MessageBusConfigResolver(messageBusConfig.factory)
        val (resolvedConfig, kafkaProperties) = configResolver.resolve(messageBusConfig, producerConfig)
        return try {
            val producer = createKafkaProducer(kafkaProperties)
            CordaKafkaProducerImpl(resolvedConfig, producer)
        } catch (ex: KafkaException) {
            val message = "SubscriptionProducerBuilderImpl failed to producer with clientId ${producerConfig.clientId}."
            log.error(message, ex)
            throw CordaMessageAPIFatalException(message, ex)
        }
    }

    private fun createKafkaProducer(kafkaProperties: Properties): KafkaProducer<Any, Any> {
        val contextClassLoader = Thread.currentThread().contextClassLoader

        return try {
            Thread.currentThread().contextClassLoader = null
            KafkaProducer(
                kafkaProperties,
                CordaAvroSerializerImpl(avroSchemaRegistry),
                CordaAvroSerializerImpl(avroSchemaRegistry)
            )
        } finally {
            Thread.currentThread().contextClassLoader = contextClassLoader
        }
    }
}
