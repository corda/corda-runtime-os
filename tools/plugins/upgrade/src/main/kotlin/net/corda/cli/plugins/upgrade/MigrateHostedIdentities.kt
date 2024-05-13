package net.corda.cli.plugins.upgrade

import net.corda.data.p2p.HostedIdentityEntry
import net.corda.messagebus.kafka.serialization.CordaAvroDeserializerImpl
import net.corda.schema.Schemas
import net.corda.schema.registry.impl.AvroSchemaRegistryImpl
import net.corda.utilities.classload.executeWithThreadContextClassLoader
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import picocli.CommandLine.ExitCode
import java.io.FileInputStream
import java.time.Duration
import java.util.Properties
import java.util.UUID
import java.util.concurrent.Callable

@CommandLine.Command(
    name = "migrate-data-5-2-1",
    description = ["Read hosted identity records from Kafka and generate SQL to persist them to the database."],
    mixinStandardHelpOptions = true,
)
class MigrateHostedIdentities : Callable<Int> {

    private companion object {
        const val POLL_TIMEOUT_MS = 3000L
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CommandLine.Option(
        names = ["-b", "--bootstrap-server"],
        description = ["Bootstrap server address."],
    )
    var bootstrapServer: String = ""

    @CommandLine.Option(
        names = ["-k", "--kafka-config"],
        description = ["Path to Kafka configuration file."]
    )
    var kafkaConfig: String? = null

    @CommandLine.Option(
        names = ["-t", "--timeout"],
        description = ["Timeout in milliseconds to read from Kafka. Defaults to 3000."]
    )
    var timeoutMs: Long = POLL_TIMEOUT_MS

    @CommandLine.Option(
        names = ["-n", "--name-prefix"],
        description = ["Name prefix for topics"]
    )
    var namePrefix: String = ""

    private val hostedIdentityTopic by lazy {
        namePrefix + Schemas.P2P.P2P_HOSTED_IDENTITIES_TOPIC
    }

    private val sysOut by lazy {
        LoggerFactory.getLogger("SystemOut")
    }

    private val consumerGroup = UUID.randomUUID().toString()

    override fun call(): Int {
        val consumer = executeWithThreadContextClassLoader(this::class.java.classLoader) {
            val registry = AvroSchemaRegistryImpl()
            val keyDeserializer = CordaAvroDeserializerImpl(registry, {}, String::class.java)
            val valueDeserializer = CordaAvroDeserializerImpl(registry, {}, HostedIdentityEntry::class.java)
            KafkaConsumer(getKafkaProperties(), keyDeserializer, valueDeserializer)
        }
        val allRecords = mutableListOf<HostedIdentityEntry>()
        try {
            consumer.subscribe(setOf(hostedIdentityTopic))
            do {
                val records = consumer.poll(Duration.ofMillis(timeoutMs)).let { records ->
                    logger.debug("Read {} records from topic '{}'.", records.count(), hostedIdentityTopic)
                    records.mapNotNull { it.value() as? HostedIdentityEntry }
                }
                consumer.commitSync()
                logger.trace("Read the following records from topic '{}': {}.", hostedIdentityTopic, records)
                if (records.isNotEmpty()) {
                    allRecords.addAll(records)
                }
            } while (records.isNotEmpty())
        } catch (ex: Exception) {
            logger.warn("Failed to read hosted identity records from topic '$hostedIdentityTopic'.", ex)
            return ExitCode.SOFTWARE
        } finally {
            consumer.closeConsumer()
        }
        sysOut.info("Read the following records from topic '$hostedIdentityTopic': $allRecords.")
        return ExitCode.OK
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
