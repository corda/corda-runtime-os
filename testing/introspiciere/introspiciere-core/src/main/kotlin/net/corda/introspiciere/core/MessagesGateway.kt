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
    fun readFromEnd(topic: String, schema: String): Pair<List<Msg>, Long>
    fun readFromBeginning(topic: String, schema: String): Pair<List<Msg>, Long>
    fun readFrom(topic: String, schema: String, from: Long): Pair<List<Msg>, Long>
    fun send(topic: String, message: KafkaMessage)
}

class MessagesGatewayImpl(private val kafkaConfig: KafkaConfig) : MessagesGateway {

    companion object {
        private val classes: MutableSet<Class<out SpecificRecordBase>> =
            Reflections("net.corda").getSubTypesOf(SpecificRecordBase::class.java)
    }

    override fun readFromEnd(topic: String, schema: String): Pair<List<Msg>, Long> {
        createConsumer(schema).use { consumer ->
            val partitions = consumer.topicPartitionsFor(topic)

            consumer.assign(partitions)
            consumer.seekToEnd(partitions)

            val records = consumer.poll(Duration.ofSeconds(5))
            return if (records.isEmpty) {
                // Workaround to fetch the current timestamp of the Kafka cluster if no messages are returned.
                // There must be a better way of doing it but I don't know it.
                emptyList<Msg>() to workaroundToFetchCurrentTimestampFromTheKafkaCluster()
            } else {
                val msgs = records.mapToMsg()
                msgs to msgs.maxOf { it.timestamp } + 1
            }
        }
    }

    override fun readFromBeginning(topic: String, schema: String): Pair<List<Msg>, Long> {
        createConsumer(schema).use { consumer ->
            val partitions = consumer.topicPartitionsFor(topic)

            consumer.assign(partitions)
            consumer.seekToBeginning(partitions)

            val records = consumer.poll(Duration.ofSeconds(5))
            return if (records.isEmpty) {
                return emptyList<Msg>() to 0L
            } else {
                val msgs = records.mapToMsg()
                msgs to msgs.maxOf { it.timestamp } + 1
            }
        }
    }

    override fun readFrom(topic: String, schema: String, from: Long): Pair<List<Msg>, Long> {
        createConsumer(schema).use { consumer ->
            val partitions = consumer.topicPartitionsFor(topic)

            val timestampsToSearch = partitions.associateWith { from }
            val offsets = consumer.offsetsForTimes(timestampsToSearch)

            val notNullOffsets = offsets.filter { it.value != null }.toMap()
            if (notNullOffsets.isEmpty()) {
                return emptyList<Msg>() to from
            }

            consumer.assign(notNullOffsets.keys)
            notNullOffsets.forEach { (k, v) ->
                consumer.seek(k, v.offset())
            }

            val records = consumer.poll(Duration.ofSeconds(5))
            return if (records.isEmpty) {
                return emptyList<Msg>() to from
            } else {
                val msgs = records.mapToMsg()
                msgs to msgs.maxOf { it.timestamp } + 1
            }
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

    private fun workaroundToFetchCurrentTimestampFromTheKafkaCluster(): Long {
        val topicGateway = TopicGatewayImpl(kafkaConfig)
        val hackTopic = "hack-to-fetch-timestamp-" + Instant.now().toEpochMilli()
        try {
            topicGateway.create(TopicDefinition(hackTopic))
            send(hackTopic, KafkaMessage.create(hackTopic, null, DemoRecord(0)))
            while (true) {
                val (msgs, timestamp) = readFrom(hackTopic, DemoRecord::class.qualifiedName!!, 0)
                if (msgs.isNotEmpty()) return timestamp
            }
        } finally {
            topicGateway.removeByName(hackTopic)
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

    private fun createConsumer(schema: String): KafkaConsumer<String, out Any> {
        return KafkaConsumer(
            mapOf(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaConfig.brokers),
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