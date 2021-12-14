package net.corda.p2p.deployment.commands

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import java.lang.Integer.min

class KafkaSetup(
    private val namespaceName: String,
    private val kafkaBrokersCount: Int,
    private val defaultPartitionsCount: Int,
) : Runnable {
    override fun run() {
        println("Setting up kafka topics...")
        val topics = listOf("ConfigTopic", "session.out.partitions", "p2p.out.markers.state").map {
            mapOf(
                "topicName" to it,
                "numPartitions" to defaultPartitionsCount,
                "replicationFactor" to min(3, kafkaBrokersCount),
                "config" to mapOf(
                    "cleanup" to mapOf("policy" to "compact"),
                    "segment" to mapOf("ms" to 300000),
                    "min" to mapOf(
                        "compaction" to
                            mapOf("lag" to mapOf("ms" to 60000)),
                        "cleanable" to
                            mapOf(
                                "dirty" to mapOf("ratio" to 0.5)
                            )
                    ),
                )
            )
        } + mapOf(
            "topicName" to "p2p.out.markers",
            "numPartitions" to defaultPartitionsCount,
            "replicationFactor" to min(3, kafkaBrokersCount),
            "config" to mapOf(
                "cleanup" to mapOf("policy" to "delete"),
            )
        )
        val config = mapOf(
            "topics" to topics
        )
        val configurationFile = File.createTempFile("kafka-setup", ".json")
        configurationFile.deleteOnExit()
        ObjectMapper().writer().writeValue(configurationFile, config)

        RunJar.startTelepresence()
        RunJar(
            "kafka-setup",
            listOf(
                "--topic", configurationFile.absolutePath,
                "--kafka",
                RunJar.kafkaFile(namespaceName).absolutePath
            )
        ).run()
    }
}
