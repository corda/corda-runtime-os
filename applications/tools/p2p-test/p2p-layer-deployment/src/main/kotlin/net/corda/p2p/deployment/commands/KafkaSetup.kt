package net.corda.p2p.deployment.commands

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.schema.Schemas.Companion.getStateAndEventDLQTopic
import net.corda.schema.Schemas.Companion.getStateAndEventStateTopic
import net.corda.schema.Schemas.Config.Companion.CONFIG_TOPIC
import net.corda.schema.Schemas.P2P.Companion.GATEWAY_TLS_CERTIFICATES
import net.corda.schema.Schemas.P2P.Companion.GATEWAY_TLS_TRUSTSTORES
import net.corda.schema.Schemas.P2P.Companion.LINK_IN_TOPIC
import net.corda.schema.Schemas.P2P.Companion.LINK_OUT_TOPIC
import net.corda.schema.Schemas.P2P.Companion.P2P_IN_TOPIC
import net.corda.schema.Schemas.P2P.Companion.P2P_OUT_MARKERS
import net.corda.schema.Schemas.P2P.Companion.P2P_OUT_TOPIC
import net.corda.schema.Schemas.P2P.Companion.SESSION_OUT_PARTITIONS
import net.corda.schema.TestSchema.Companion.APP_RECEIVED_MESSAGES_TOPIC
import net.corda.schema.TestSchema.Companion.CRYPTO_KEYS_TOPIC
import net.corda.schema.TestSchema.Companion.GROUP_POLICIES_TOPIC
import net.corda.schema.TestSchema.Companion.HOSTED_MAP_TOPIC
import net.corda.schema.TestSchema.Companion.MEMBER_INFO_TOPIC
import java.io.File
import java.lang.Integer.min

class KafkaSetup(
    private val namespaceName: String,
    private val kafkaBrokersCount: Int,
    private val defaultPartitionsCount: Int,
) : Runnable {
    companion object {
        private val compactedTopics = listOf(
            CONFIG_TOPIC,
            GATEWAY_TLS_CERTIFICATES,
            GATEWAY_TLS_TRUSTSTORES,
            CRYPTO_KEYS_TOPIC,
            GROUP_POLICIES_TOPIC,
            HOSTED_MAP_TOPIC,
            SESSION_OUT_PARTITIONS,
            MEMBER_INFO_TOPIC,
            getStateAndEventStateTopic(P2P_OUT_MARKERS),
        )
        private val nonCompactedTopics = listOf(
            APP_RECEIVED_MESSAGES_TOPIC,
            LINK_IN_TOPIC,
            LINK_OUT_TOPIC,
            P2P_IN_TOPIC,
            P2P_OUT_TOPIC,
            P2P_OUT_MARKERS,
            getStateAndEventDLQTopic(P2P_OUT_MARKERS),
        )
    }

    override fun run() {
        println("Setting up kafka topics...")
        val topics = compactedTopics.map {
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
        } + nonCompactedTopics.map {
            mapOf(
                "topicName" to it,
                "numPartitions" to defaultPartitionsCount,
                "replicationFactor" to min(3, kafkaBrokersCount),
                "config" to mapOf(
                    "cleanup" to mapOf("policy" to "delete"),
                )
            )
        }
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
