package net.corda.introspiciere.core

import net.corda.messagebus.kafka.serialization.CordaAvroDeserializerImpl
import net.corda.schema.registry.impl.AvroSchemaRegistryImpl
import org.apache.avro.specific.SpecificRecordBase
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.reflections.Reflections
import java.net.InetAddress
import java.nio.ByteBuffer
import java.time.Duration

class ReadMessagesUseCases(
    private val kafkaConfig: KafkaConfig,
) : UseCase<ReadMessagesUseCases.Input> {

    data class Input(
        val topic: String,
        val key: String,
        val schemaClass: String,
        val startFromBeginning: Boolean = true,
        val startFromEnd: Boolean = false,
        val startingOffsets: List<Long>? = null,
    )

    interface Output {
        fun messages(byteArrays: List<ByteArray>)
        fun latestOffsets(offsets: LongArray)
    }

    companion object {
        private val classes: MutableSet<Class<out SpecificRecordBase>> by lazy {
            Reflections("net.corda").getSubTypesOf(SpecificRecordBase::class.java)
        }
    }

    fun beginningOffsets(topic: String, schemaClass: String): LongArray {
        return offsets(topic, schemaClass, true)
    }

    fun endOffsets(topic: String, schemaClass: String): LongArray {
        return offsets(topic, schemaClass, false)
    }

    private fun offsets(topic: String, schemaClass: String, fromBeginning: Boolean): LongArray {
        createConsumer(schemaClass).use { consumer ->
            val partitions = consumer.partitionsFor(topic).map {
                TopicPartition(it.topic(), it.partition())
            }

            val offsets =
                if (fromBeginning) consumer.beginningOffsets(partitions)
                else consumer.endOffsets(partitions)

            val array = LongArray(partitions.size)
            offsets.forEach { (partition, offset) ->
                array[partition.partition()] = offset
            }

            return array
        }
    }

    fun readFrom(topic: String, key: String, schemaClass: String, from: List<Long>, output: Output) {
        createConsumer(schemaClass).use { consumer ->

            val partitions = consumer.partitionsFor(topic)
                .map { TopicPartition(it.topic(), it.partition()) }

            consumer.assign(partitions)

            for (topicPartition in partitions) {
                consumer.seek(topicPartition, from[topicPartition.partition()])
            }

            val records = consumer.poll(Duration.ofSeconds(5)).filter { it.key() == key }
            if (records.isNotEmpty()) {
                val toByteBufferMethod = records.first().value()::class.java.getMethod("toByteBuffer")
                val values = records.map { toByteBufferMethod.invoke(it.value()) as ByteBuffer }
                val byteArrays = values.map { it.toByteArray() }
                output.messages(byteArrays)
            }

            val latests = from.toLongArray()
            records.forEach { latests[it.partition()] = it.offset() + 1 }
            output.latestOffsets(latests)
        }
    }

    private fun createConsumer(schemaClass: String): KafkaConsumer<String, out Any> {
        val props = mapOf(
            ConsumerConfig.CLIENT_ID_CONFIG to InetAddress.getLocalHost().hostName,
            ConsumerConfig.GROUP_ID_CONFIG to "foo",
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaConfig.brokers,
        )

        return KafkaConsumer(
            props,
            CordaAvroDeserializerImpl(
                AvroSchemaRegistryImpl(specificRecordaBaseClasses = classes), {}, String::class.java),
            CordaAvroDeserializerImpl(
                AvroSchemaRegistryImpl(specificRecordaBaseClasses = classes), {}, Class.forName(schemaClass))
        )
    }

    private fun ByteBuffer.toByteArray(): ByteArray {
        val array = ByteArray(remaining())
        get(array)
        return array
    }

    override fun execute(input: Input) {
        throw NotImplementedError("ReadMessagesUseCase::execute has not been implented and should not be used.")
    }
}