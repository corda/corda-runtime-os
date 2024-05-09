package net.corda.cli.plugins.upgrade

import net.corda.data.p2p.HostedIdentityEntry
import net.corda.messagebus.kafka.serialization.CordaAvroDeserializerImpl
import net.corda.schema.Schemas
import net.corda.schema.registry.impl.AvroSchemaRegistryImpl
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import picocli.CommandLine
import java.io.FileInputStream
import java.time.Duration
import java.util.Properties
import java.util.UUID

@CommandLine.Command(
    name = "migrate-data",
    description = ["Read hosted identity records from Kafka and persist them to the database."],
    mixinStandardHelpOptions = true,
)
class MigrateHostedIdentities : Runnable {

    private companion object {
        const val POLL_TIMEOUT_MS = 3000L
    }

    @CommandLine.Option(
        names = ["-b", "--bootstrap-server"],
        description = ["Bootstrap server address."],
    )
    var bootstrapServer: String = ""

    @CommandLine.Option(
        names = ["-k", "--kafka-config"],
        description = ["Absolute path to Kafka configuration file."]
    )
    var kafkaConfig: String? = null

    @CommandLine.Option(
        names = ["-t", "--timeout"],
        description = ["Timeout in milliseconds to read from Kafka. Defaults to 3000."]
    )
    var timeoutMs: Long = POLL_TIMEOUT_MS

    @CommandLine.Option(
        names = ["-p", "--topic-prefix"],
        description = ["Kafka topic prefix"]
    )
    var topicPrefix: String = ""

    private val hostedIdentityTopic by lazy {
        topicPrefix + Schemas.P2P.P2P_HOSTED_IDENTITIES_TOPIC
    }

    private val consumerGroup = UUID.randomUUID().toString()

    override fun run() {
        // Switch ClassLoader so LoginModules can be found
        val contextCL = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = this::class.java.classLoader

        val registry = AvroSchemaRegistryImpl()
        val keyDeserializer = CordaAvroDeserializerImpl(registry, {}, String::class.java)
        val valueDeserializer = CordaAvroDeserializerImpl(registry, {}, HostedIdentityEntry::class.java)

        val consumer = KafkaConsumer(getKafkaProperties(), keyDeserializer, valueDeserializer)
        val records = try {
            consumer.subscribe(setOf(hostedIdentityTopic))

            val records = consumer.poll(Duration.ofMillis(timeoutMs)).let { records ->
                UpgradePluginWrapper.logger.debug("Read {} records from topic '{}'.", records.count(), hostedIdentityTopic)
                records.mapNotNull { it.value() as? HostedIdentityEntry }
            }
            UpgradePluginWrapper.logger.debug("Read the following records from topic '{}': {}.", hostedIdentityTopic, records)

            records
        } catch (ex: Exception) {
            UpgradePluginWrapper.logger.warn("Failed to read hosted identity records from topic '$hostedIdentityTopic'.", ex)
        } finally {
            consumer.closeConsumer()
        }

        // TODO to be replaced with persistence logic
        println("Read the following records from topic '$hostedIdentityTopic': $records.")

        Thread.currentThread().contextClassLoader = contextCL
    }

    private fun getKafkaProperties(): Properties {
        val kafkaProperties = Properties()
        if (kafkaConfig != null) {
            kafkaProperties.load(FileInputStream(kafkaConfig!!))
        }
        kafkaProperties[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = bootstrapServer
        kafkaProperties[ConsumerConfig.GROUP_ID_CONFIG] = consumerGroup
        kafkaProperties[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        return kafkaProperties
    }

    private fun KafkaConsumer<Any, Any>.closeConsumer() {
        try {
            close()
        } catch (ex: Exception) {
            UpgradePluginWrapper.logger.error("Failed to close consumer from group '$consumerGroup'.", ex)
        }
    }
}
