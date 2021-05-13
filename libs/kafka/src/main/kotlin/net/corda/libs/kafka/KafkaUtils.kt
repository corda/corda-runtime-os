package net.corda.libs.kafka

import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.errors.TopicExistsException
import java.io.FileInputStream
import java.util.*
import java.util.concurrent.ExecutionException

fun createTopic(
    topic: String,
    partitions: Int,
    replication: Short,
    kafkaProps: Properties
) {
    val newTopic = NewTopic(topic, partitions, replication)
    try {
        with(AdminClient.create(kafkaProps)) {
            createTopics(listOf(newTopic)).all().get()
        }
    } catch (e: ExecutionException) {
        if (e.cause !is TopicExistsException) throw e
    }
}

fun createProducer(props: Properties): KafkaProducer<Any, Any> {
    return KafkaProducer(props)
}

fun loadKafkaConfig (configFile: String, keySerialiser: String?, valueSerialiser: String?): Properties {
    val props = loadConfig(configFile)
    props[ProducerConfig.ACKS_CONFIG] = "all"
    props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = keySerialiser
    props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = valueSerialiser
    return props
}

private fun loadConfig(configFile: String) = FileInputStream(configFile).use {
    Properties().apply {
        load(it)
    }
}
