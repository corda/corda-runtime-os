package net.corda.tools.kafka

import com.typesafe.config.ConfigFactory
import net.corda.libs.kafka.createTopic
import net.corda.libs.kafka.loadKafkaConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.serialization.StringSerializer
import java.io.File
import kotlin.system.exitProcess


fun main(args: Array<String>) {
    if (args.size != 3) {
        println("Required command line arguments: kafkaconfig topicName typesafeconfig")
        exitProcess(1)
    }

    val kafkaProps = loadKafkaConfig(args[0])

    val topic = args[1]
    createTopic(topic, 1, 1, kafkaProps)

    val configuration = ConfigFactory.parseString(File(args[2]).readText())

    kafkaProps[ProducerConfig.ACKS_CONFIG] = "all"
    kafkaProps[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.qualifiedName
    kafkaProps[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.qualifiedName

    KafkaProducer<String, String>(kafkaProps).use { producer ->
        for (key in configuration.root().keys) {
            val record = configuration.atKey(key).toString()
            println("Producing record: $key\t$record")

            producer.send(ProducerRecord(topic, key, record)) { m: RecordMetadata, e: Exception? ->
                when (e) {
                    null -> println("Produced record to topic ${m.topic()} partition [${m.partition()}] @ offset ${m.offset()}")
                    else -> e.printStackTrace()
                }
            }
        }
        producer.flush()
    }
}
