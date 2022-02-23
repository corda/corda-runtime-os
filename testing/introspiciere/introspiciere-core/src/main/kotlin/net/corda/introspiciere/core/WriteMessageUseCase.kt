package net.corda.introspiciere.core

import net.corda.messagebus.kafka.serialization.CordaAvroSerializerImpl
import net.corda.schema.registry.impl.AvroSchemaRegistryImpl
import org.apache.avro.specific.SpecificRecordBase
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.reflections.Reflections
import java.net.InetAddress
import java.nio.ByteBuffer

class WriteMessageUseCase(private val kafkaConfig: KafkaConfig) : UseCase<WriteMessageUseCase.Input> {

    data class Input(
        val topic: String,
        val key: String,
        val schema: ByteArray,
        val schemaClass: String,
    )

    override fun execute(input: Input) {
        val clss = Class.forName(input.schemaClass)

        val method = clss.getMethod("fromByteBuffer", ByteBuffer::class.java)
        val keyPairEntry = method.invoke(null, ByteBuffer.wrap(input.schema))

        val props = mapOf(
            ProducerConfig.CLIENT_ID_CONFIG to InetAddress.getLocalHost().hostName,
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaConfig.brokers,
            ProducerConfig.ACKS_CONFIG to "all"
        )

        val producer = KafkaProducer(
            props,
            CordaAvroSerializerImpl(AvroSchemaRegistryImpl(specificRecordaBaseClasses = classes), String::class.java),
            CordaAvroSerializerImpl(AvroSchemaRegistryImpl(specificRecordaBaseClasses = classes), Any::class.java)
        )

        producer.use {
            val record = ProducerRecord(input.topic, input.key, keyPairEntry)
            it.send(record).get()
        }
    }

    private val classes: MutableSet<Class<out SpecificRecordBase>> by lazy {
        Reflections("net.corda").getSubTypesOf(SpecificRecordBase::class.java)
    }
}