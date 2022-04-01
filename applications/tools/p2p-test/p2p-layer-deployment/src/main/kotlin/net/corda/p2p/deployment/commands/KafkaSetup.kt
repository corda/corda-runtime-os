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
import java.io.StringWriter
import java.lang.Integer.min

class KafkaSetup(
    private val namespaceName: String,
    private val kafkaBrokersCount: Int,
    private val defaultPartitionsCount: Int,
) : Runnable {
    companion object {
        private val topicsToCompacted = mapOf(
            APP_RECEIVED_MESSAGES_TOPIC to false,
            CONFIG_TOPIC to true,
            CRYPTO_KEYS_TOPIC to true,
            GATEWAY_TLS_CERTIFICATES to true,
            GATEWAY_TLS_TRUSTSTORES to true,
            GROUP_POLICIES_TOPIC to true,
            HOSTED_MAP_TOPIC to true,
            LINK_IN_TOPIC to false,
            LINK_OUT_TOPIC to false,
            MEMBER_INFO_TOPIC to true,
            P2P_IN_TOPIC to false,
            P2P_OUT_MARKERS to false,
            getStateAndEventDLQTopic(P2P_OUT_MARKERS) to false,
            getStateAndEventStateTopic(P2P_OUT_MARKERS) to true,
            P2P_OUT_TOPIC to false,
            SESSION_OUT_PARTITIONS to true,
        )

        private val compactedConfig = mapOf(
            "cleanup" to mapOf("policy" to "compact"),
            "segment" to mapOf("ms" to 300000),
            "delete" to mapOf("retention" to mapOf("ms" to 300000)),
            "min" to mapOf(
                "compaction" to mapOf("lag" to mapOf("ms" to 60000)),
                "cleanable" to mapOf("dirty" to mapOf("ratio" to 0.5))
            ),
            "max" to mapOf("compaction" to mapOf("lag" to mapOf("ms" to 300000)))
        )
        private val simpleConfig = mapOf(
            "cleanup" to mapOf("policy" to "delete"),
        )
    }

    internal fun createConfiguration(): String {
        val config = mapOf(
            "topics" to
                topicsToCompacted.map { (name, compacted) ->
                    val config = if (compacted) {
                        compactedConfig
                    } else {
                        simpleConfig
                    }
                    mapOf(
                        "topicName" to name,
                        "numPartitions" to defaultPartitionsCount,
                        "replicationFactor" to min(3, kafkaBrokersCount),
                        "config" to config
                    )
                }
        )
        return StringWriter().use {
            ObjectMapper().writer().writeValue(it, config)
            it.toString()
        }
    }

    override fun run() {
        println("Setting up kafka topics...")
        val configurationFile = File.createTempFile("kafka-setup", ".json")
        configurationFile.deleteOnExit()
        configurationFile.writeText(createConfiguration())

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
