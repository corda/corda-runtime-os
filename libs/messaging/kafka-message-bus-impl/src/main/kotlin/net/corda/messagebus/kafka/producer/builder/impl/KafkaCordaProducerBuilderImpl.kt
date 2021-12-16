package net.corda.messagebus.kafka.producer.builder.impl

import com.typesafe.config.Config
import net.corda.data.CordaAvroSerializationFactory
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.builder.CordaProducerBuilder
import net.corda.messagebus.kafka.producer.wrapper.impl.CordaKafkaProducerImpl
import net.corda.messagebus.kafka.serialization.CordaAvroSerializerImpl
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.kafka.utils.toProperties
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.util.contextLogger
import org.apache.kafka.clients.CommonClientConfigs.CLIENT_ID_CONFIG
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.KafkaException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger

/**
 * Builder for a Kafka Producer.
 * Initialises producer for transactions if publisherConfig contains an instanceId.
 * Producer uses avro for serialization.
 * If fatal exception is thrown in the construction of a KafKaProducer
 * then it is closed and exception is thrown as [CordaMessageAPIFatalException].
 */
@Component(service = [CordaProducerBuilder::class])
class KafkaCordaProducerBuilderImpl @Activate constructor(
    @Reference(service = CordaAvroSerializationFactory::class)
    private val avroSerializationFactory: CordaAvroSerializationFactory,
    @Reference(service = AvroSchemaRegistry::class)
    private val avroSchemaRegistry: AvroSchemaRegistry
) : CordaProducerBuilder {

    companion object {
        private val log: Logger = contextLogger()
    }

    override fun createProducer(producerConfig: Config): CordaProducer {
        val contextClassLoader = Thread.currentThread().contextClassLoader
        val producer = try {
            Thread.currentThread().contextClassLoader = null;
            KafkaProducer(
                producerConfig.toProperties(),
                // ???? What's the type here?
                CordaAvroSerializerImpl(avroSchemaRegistry),
                CordaAvroSerializerImpl(avroSchemaRegistry)
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
