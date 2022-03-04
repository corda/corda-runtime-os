package net.corda.introspiciere.core

import net.corda.data.demo.DemoRecord
import net.corda.introspiciere.domain.KafkaMessage
import net.corda.introspiciere.domain.TopicDefinition
import net.corda.introspiciere.payloads.Msg
import net.corda.messagebus.kafka.serialization.CordaAvroDeserializerImpl
import net.corda.messagebus.kafka.serialization.CordaAvroSerializerImpl
import net.corda.schema.registry.impl.AvroSchemaRegistryImpl
import org.apache.avro.specific.SpecificRecordBase
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.reflections.Reflections
import java.nio.ByteBuffer
import java.time.Duration
import java.time.Instant

interface MessagesGateway {
    fun readFrom(topic: String, schema: String, from: Long, timeout: Duration): Pair<List<Msg>, Long>
    fun send(topic: String, message: KafkaMessage)
}

class MessagesGatewayImpl(private val kafkaConfig: KafkaConfig) : MessagesGateway {

    companion object {
        private val classes: MutableSet<Class<out SpecificRecordBase>> =
            Reflections("net.corda").getSubTypesOf(SpecificRecordBase::class.java)
    }

    override fun readFrom(topic: String, schema: String, from: Long, timeout: Duration): Pair<List<Msg>, Long> {
        createConsumer(schema).use { consumer ->
            val partitions = consumer.topicPartitionsFor(topic)

            val timestampsToSearch = partitions.associateWith { from }
            val offsets = consumer.offsetsForTimes(timestampsToSearch)

            val notNullOffsets = offsets.filter { it.value != null }.toMap()
            if (notNullOffsets.isEmpty()) {
                return emptyList<Msg>() to from
            }

            consumer.assign(partitions)
            notNullOffsets.forEach { (k, v) ->
                consumer.seek(k, v.offset())
            }

            val messages = consumer.poll(timeout).mapToMsg()
            val maxTimestamp = messages.maxOfOrNull { it.timestamp } ?: from
            return messages to maxTimestamp
        }
    }

    private fun KafkaConsumer<String, out Any>.topicPartitionsFor(topic: String): List<TopicPartition> =
        partitionsFor(topic).map { TopicPartition(it.topic(), it.partition()) }

    private fun ConsumerRecords<String, out Any>.mapToMsg(): List<Msg> {
        val toByteBufferMethod = this.first().value()::class.java.getMethod("toByteBuffer")
        return this.map {
            val value = toByteBufferMethod.invoke(it.value()) as ByteBuffer
            Msg(it.timestamp(), it.key(), value.toByteArray())
        }
    }

    override fun send(topic: String, message: KafkaMessage) {
        createProducer().use { producer ->
            val record = ProducerRecord(topic, message.key, message.toAny())
            producer.send(record).get()
        }
    }

    private fun createProducer(): KafkaProducer<String, Any> = KafkaProducer(
        mapOf(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaConfig.brokers),
        CordaAvroSerializerImpl(AvroSchemaRegistryImpl(specificRecordaBaseClasses = classes), String::class.java),
        CordaAvroSerializerImpl(AvroSchemaRegistryImpl(specificRecordaBaseClasses = classes), Any::class.java)
    )

    private fun createConsumer(
        schema: String,
        overrideConfig: Map<String, Any> = emptyMap(),
    ): KafkaConsumer<String, out Any> {
        return KafkaConsumer(
            mapOf(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaConfig.brokers) + overrideConfig,
            CordaAvroDeserializerImpl(
                AvroSchemaRegistryImpl(specificRecordaBaseClasses = classes), {}, String::class.java),
            CordaAvroDeserializerImpl(
                AvroSchemaRegistryImpl(specificRecordaBaseClasses = classes), {}, Class.forName(schema))
        )
    }

    private fun ByteBuffer.toByteArray(): ByteArray {
        val array = ByteArray(remaining())
        get(array)
        return array
    }
}