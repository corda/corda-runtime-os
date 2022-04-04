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
        private val topicsToConfig = mapOf(
            APP_RECEIVED_MESSAGES_TOPIC to simpleConfig,
            CONFIG_TOPIC to compactedConfig,
            CRYPTO_KEYS_TOPIC to compactedConfig,
            GATEWAY_TLS_CERTIFICATES to compactedConfig,
            GATEWAY_TLS_TRUSTSTORES to compactedConfig,
            GROUP_POLICIES_TOPIC to compactedConfig,
            HOSTED_MAP_TOPIC to compactedConfig,
            LINK_IN_TOPIC to simpleConfig,
            LINK_OUT_TOPIC to simpleConfig,
            MEMBER_INFO_TOPIC to compactedConfig,
            P2P_IN_TOPIC to simpleConfig,
            P2P_OUT_MARKERS to simpleConfig,
            getStateAndEventDLQTopic(P2P_OUT_MARKERS) to simpleConfig,
            getStateAndEventStateTopic(P2P_OUT_MARKERS) to compactedConfig,
            P2P_OUT_TOPIC to simpleConfig,
            SESSION_OUT_PARTITIONS to compactedConfig,
        )
    }

    internal fun createConfiguration(): String {
        val config = mapOf(
            "topics" to
                topicsToConfig.map { (name, config) ->
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
