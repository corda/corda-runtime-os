package net.corda.cli.plugins.upgrade

import com.fasterxml.jackson.module.kotlin.readValue
import net.corda.cli.plugins.topicconfig.Create
import net.corda.cli.plugins.topicconfig.CreateConnect
import net.corda.cli.plugins.upgrade.UpgradePluginWrapper.UpgradePlugin
import net.corda.data.p2p.HostedIdentityEntry
import net.corda.messagebus.kafka.serialization.CordaAvroDeserializerImpl
import net.corda.schema.Schemas
import net.corda.schema.registry.impl.AvroSchemaRegistryImpl
import org.apache.kafka.clients.admin.Admin
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import java.util.Properties
import java.util.UUID

@CommandLine.Command(
    name = "migrate-data-5-2-1",
    description = ["Read hosted identity records from Kafka and generate SQL to persist them to the database."],
    mixinStandardHelpOptions = true,
)
class MigrateHostedIdentities : Runnable {

    private companion object {
        const val POLL_TIMEOUT_MS = 3000L
        val logger: Logger = LoggerFactory.getLogger(UpgradePlugin::class.java)
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

    @CommandLine.Option(
        names = ["-f", "--topic-config"],
        description = ["Path to Kafka topic configuration file in YAML format"]
    )
    var topicConfig: String? = null

    private val hostedIdentityTopic by lazy {
        namePrefix + Schemas.P2P.P2P_HOSTED_IDENTITIES_TOPIC
    }

    private val consumerGroup = UUID.randomUUID().toString()

    override fun run() {
        // Switch ClassLoader so LoginModules can be found
        val contextCL = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = this::class.java.classLoader

        val kafkaProperties = getKafkaProperties()
        createAcls(kafkaProperties)
        val registry = AvroSchemaRegistryImpl()
        val keyDeserializer = CordaAvroDeserializerImpl(registry, {}, String::class.java)
        val valueDeserializer = CordaAvroDeserializerImpl(registry, {}, HostedIdentityEntry::class.java)
        val consumer = KafkaConsumer(kafkaProperties, keyDeserializer, valueDeserializer)
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
        } finally {
            consumer.closeConsumer()
        }

        // TODO replace with persistence logic in CORE-20426
        println("Read the following records from topic '$hostedIdentityTopic': $allRecords.")

        Thread.currentThread().contextClassLoader = contextCL
    }

    private fun createAcls(kafkaProperties: Properties) {
        try {
            val client = Admin.create(kafkaProperties)
            require(topicConfig != null) { "Topic configuration file was not provided." }
            val topicConfigs: Create.PreviewTopicConfigurations =
                Create().mapper.readValue(Files.readString(Paths.get(topicConfig!!)))
            client.createAcls(CreateConnect().getAclBindings(topicConfigs.acls)).all().get()
        } catch (ex: Exception) {
            logger.warn("Failed to create Kafka ACLs. Cause: ${ex.message}", ex)
        }
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
