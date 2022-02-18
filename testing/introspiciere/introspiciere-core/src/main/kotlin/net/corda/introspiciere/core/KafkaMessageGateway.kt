package net.corda.introspiciere.core

import net.corda.introspiciere.domain.KafkaMessage
import net.corda.messagebus.kafka.serialization.CordaAvroSerializerImpl
import net.corda.schema.registry.impl.AvroSchemaRegistryImpl
import org.apache.avro.specific.SpecificRecordBase
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.reflections.Reflections
import java.net.InetAddress
import java.nio.ByteBuffer


class KafkaMessageGateway(private val servers: List<String>) {

    fun send(kafkaMessage: KafkaMessage) {
        val clss = Class.forName(kafkaMessage.schemaClass)

        val method = clss.getMethod("fromByteBuffer", ByteBuffer::class.java)
        val keyPairEntry = method.invoke(null, ByteBuffer.wrap(kafkaMessage.schema))

        val props = mapOf(
            ProducerConfig.CLIENT_ID_CONFIG to InetAddress.getLocalHost().hostName,
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to servers.joinToString(","),
            ProducerConfig.ACKS_CONFIG to "all"
        ).toProperties()

        val producer = KafkaProducer(
            props,
            CordaAvroSerializerImpl(AvroSchemaRegistryImpl(specificRecordaBaseClasses = classes), String::class.java),
            CordaAvroSerializerImpl(AvroSchemaRegistryImpl(specificRecordaBaseClasses = classes), Any::class.java)
        )

        producer.use {
            val record = ProducerRecord(kafkaMessage.topic, kafkaMessage.key, keyPairEntry)
            it.send(record).get()
        }
    }

    private val classes: MutableSet<Class<out SpecificRecordBase>> by lazy {
        Reflections("net.corda").getSubTypesOf(SpecificRecordBase::class.java)
    }
}