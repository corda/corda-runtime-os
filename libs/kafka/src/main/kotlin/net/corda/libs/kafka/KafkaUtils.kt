package net.corda.libs.kafka

import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
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

fun loadKafkaConfig(configFile: String) = FileInputStream(configFile).use {
    Properties().apply {
        load(it)
    }
}
