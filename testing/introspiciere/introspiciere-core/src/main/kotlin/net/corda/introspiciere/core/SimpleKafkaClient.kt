package net.corda.introspiciere.core

import net.corda.messagebus.kafka.serialization.CordaAvroDeserializerImpl
import net.corda.messagebus.kafka.serialization.CordaAvroSerializerImpl
import net.corda.schema.registry.impl.AvroSchemaRegistryImpl
import org.apache.avro.specific.SpecificRecordBase
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.admin.AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.reflections.Reflections
import java.net.InetAddress
import java.time.Duration
import java.util.*

class SimpleKafkaClient(val servers: List<String>) {
    val classes: MutableSet<Class<out SpecificRecordBase>> by lazy {
        Reflections("net.corda").getSubTypesOf(SpecificRecordBase::class.java)
    }

    fun fetchTopics(): String {
        val properties = Properties()
        properties[BOOTSTRAP_SERVERS_CONFIG] = servers.joinToString(",")
        //properties[AdminClientConfig.RETRIES_CONFIG] = 5//Int.MAX_VALUE
        //properties[AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG] = 20000 //Must be equal or bigger than REQUEST_TIMEOUT_MS_CONFIG
        //properties[AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG] = 5000

        val topicNamesList = Admin.create(properties).use {
            it.listTopics().names().get()
        }

        // TODO make this json
        val topicNames = topicNamesList.joinToString()

        println("Topics: $topicNames")
        return topicNames
    }

    fun <K : Any, V : Any> send(topic: String, key: K, value: V) {
        val config = Properties()
        config[ConsumerConfig.CLIENT_ID_CONFIG] = InetAddress.getLocalHost().hostName
        config[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = servers.joinToString(",")
        config["acks"] = "all"

        val record = ProducerRecord(topic, key, value)
        val producer = KafkaProducer<K, V>(
            config,
            CordaAvroSerializerImpl(AvroSchemaRegistryImpl(specificRecordaBaseClasses = classes)),
            CordaAvroSerializerImpl(AvroSchemaRegistryImpl(specificRecordaBaseClasses = classes))
        )

        producer.use {
            it.send(record).get()
        }

        println("Successfully sent message in topic $topic")
    }

    inline fun <reified V : Any> read(
        topic: String,
        timeout: Duration = Duration.ofSeconds(5),
    ) = read<Void, V>(topic, null, timeout)

    inline fun <reified K : Any, reified V : Any> read(
        topic: String,
        key: K? = null,
        timeout: Duration = Duration.ofSeconds(5),
    ): List<V> {
        val config = Properties()
        config[ConsumerConfig.CLIENT_ID_CONFIG] = InetAddress.getLocalHost().hostName
        config[ConsumerConfig.GROUP_ID_CONFIG] = "foo"
        config[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = servers.joinToString(":")
        config[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"

        val consumer = KafkaConsumer(
            config,
            CordaAvroDeserializerImpl(
                AvroSchemaRegistryImpl(specificRecordaBaseClasses = classes), {}, K::class.java),
            CordaAvroDeserializerImpl(
                AvroSchemaRegistryImpl(specificRecordaBaseClasses = classes), {}, V::class.java)
        )

        consumer.use { cons ->
            consumer.subscribe(listOf(topic))
            val records = cons.poll(timeout)
            return records.filter { key == null || it.key() == key }.map { it.value() }
        }
    }
}