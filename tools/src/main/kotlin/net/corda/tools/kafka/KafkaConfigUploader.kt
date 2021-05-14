package net.corda.tools.kafka

import com.typesafe.config.ConfigFactory
import net.corda.libs.kafka.topic.utils.KafkaTopicUtils
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.serialization.StringSerializer
import java.io.File
import java.io.StringReader
import java.util.*
import kotlin.system.exitProcess


fun main(args: Array<String>) {
    if (args.size != 3) {
        println("Required command line arguments: kafkaServerProperty topicName typesafeconfig")
        exitProcess(1)
    }

    val kafkaProps = Properties()
    kafkaProps.load(StringReader(args[0]))

    val topicName = args[1]
    KafkaTopicUtils.createTopic(topicName, 1, 1, kafkaProps)

    val configuration = ConfigFactory.parseString(File(args[2]).readText())

    val producer =
        KafkaTopicUtils.createProducer(kafkaProps, StringSerializer::class.qualifiedName, StringSerializer::class.qualifiedName)
    for (key in configuration.root().keys) {
        val record = configuration.atKey(key).toString()
        println("Producing record: $key\t$record")

        producer.send(ProducerRecord(topicName, key, record)) { m: RecordMetadata, e: Exception? ->
            when (e) {
                null -> println("Produced record to topic ${m.topic()} partition [${m.partition()}] @ offset ${m.offset()}")
                else -> e.printStackTrace()
            }
        }
    }
    producer.flush()
}
