package net.corda.introspiciere.core

import net.corda.introspiciere.domain.KafkaMessage
import net.corda.messagebus.kafka.serialization.CordaAvroDeserializerImpl
import net.corda.schema.registry.impl.AvroSchemaRegistryImpl
import org.apache.avro.specific.SpecificRecordBase
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.reflections.Reflections
import java.net.InetAddress
import java.nio.ByteBuffer
import java.time.Duration

class KafkaReaderGateway(private val servers: List<String>) {

    fun read(topic: String, key: String, schemaClass: String): List<KafkaMessage> {
        val props = mapOf(
            ConsumerConfig.CLIENT_ID_CONFIG to InetAddress.getLocalHost().hostName,
            ConsumerConfig.GROUP_ID_CONFIG to "foo",
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to servers.joinToString(","),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest"
        ).toProperties()

        val consumer = KafkaConsumer(
            props,
            CordaAvroDeserializerImpl(
                AvroSchemaRegistryImpl(specificRecordaBaseClasses = classes), {}, String::class.java),
            CordaAvroDeserializerImpl(
                AvroSchemaRegistryImpl(specificRecordaBaseClasses = classes), {}, Class.forName(schemaClass))
        )

        consumer.use { cons ->
            consumer.subscribe(listOf(topic))
            val records = cons.poll(Duration.ofSeconds(10))
            return records.filter { it.key() == key }.map {
                val value = it.value()
                val byteBuffer = value::class.java.getMethod("toByteBuffer").invoke(value) as ByteBuffer
                KafkaMessage(topic, key, byteBuffer.toByteArray(), schemaClass)
            }
        }
    }

    private val classes: MutableSet<Class<out SpecificRecordBase>> by lazy {
        Reflections("net.corda").getSubTypesOf(SpecificRecordBase::class.java)
    }

    private fun ByteBuffer.toByteArray(): ByteArray {
        val array = ByteArray(remaining())
        get(array)
        return array
    }
}