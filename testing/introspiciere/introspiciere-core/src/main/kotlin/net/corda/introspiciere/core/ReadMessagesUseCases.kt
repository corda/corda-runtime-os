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

class ReadMessagesUseCases(
    private val kafkaConfig: KafkaConfig,
    private val presenter: Presenter<List<KafkaMessage>>,
) : UseCase<ReadMessagesUseCases.Input> {

    data class Input(val topic: String, val key: String, val schemaClass: String)

    override fun execute(input: Input) {
        val props = mapOf(
            ConsumerConfig.CLIENT_ID_CONFIG to InetAddress.getLocalHost().hostName,
            ConsumerConfig.GROUP_ID_CONFIG to "foo",
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaConfig.brokers,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest"
        )

        val consumer = KafkaConsumer(
            props,
            CordaAvroDeserializerImpl(
                AvroSchemaRegistryImpl(specificRecordaBaseClasses = classes), {}, String::class.java),
            CordaAvroDeserializerImpl(
                AvroSchemaRegistryImpl(specificRecordaBaseClasses = classes), {}, Class.forName(input.schemaClass))
        )

        consumer.use { cons ->
            consumer.subscribe(listOf(input.topic))
            val records = cons.poll(Duration.ofSeconds(10)).filter { it.key() == input.key }.map {
                val value = it.value()
                val byteBuffer = value::class.java.getMethod("toByteBuffer").invoke(value) as ByteBuffer
                KafkaMessage(input.topic, input.key, byteBuffer.toByteArray(), input.schemaClass)
            }
            presenter.present(records)
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