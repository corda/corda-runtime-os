package net.corda.cli.plugins.upgrade

import net.corda.cli.plugins.topicconfig.TopicPlugin
import net.corda.data.p2p.HostedIdentityEntry
import net.corda.messagebus.kafka.serialization.CordaAvroDeserializerImpl
import net.corda.schema.Schemas
import net.corda.schema.registry.impl.AvroSchemaRegistryImpl
import org.apache.kafka.clients.consumer.KafkaConsumer
import picocli.CommandLine
import java.time.Duration

@CommandLine.Command(
    name = "read-kafka",
    description = ["Read hosted identity records from Kafka"],
    mixinStandardHelpOptions = true,
)
class HostedIdentityReader : Runnable {

    private companion object {
        const val POLL_TIMEOUT_MS = 100L
    }

    @CommandLine.Option(
        names = ["-b", "--bootstrap-server"],
        description = ["Bootstrap server address"],
    )
    var bootstrapServer: String = ""

    @CommandLine.Option(
        names = ["-k", "--kafka-config"],
        description = ["Path to Kafka configuration file"]
    )
    var kafkaConfig: String? = null

    override fun run() {
        // Switch ClassLoader so LoginModules can be found
        val contextCL = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = this::class.java.classLoader

        val registry = AvroSchemaRegistryImpl()
        val keyDeserializer = CordaAvroDeserializerImpl(registry, {}, String::class.java)
        val valueDeserializer = CordaAvroDeserializerImpl(registry, {}, HostedIdentityEntry::class.java)

        val topicCommand = TopicPlugin.Topic()
        kafkaConfig?.let { topicCommand.kafkaConfig = it }
        topicCommand.bootstrapServer = bootstrapServer
        val kafkaProperties = topicCommand.getKafkaProperties()

        val consumer = KafkaConsumer(kafkaProperties, keyDeserializer, valueDeserializer)
        consumer.subscribe(setOf(Schemas.P2P.P2P_HOSTED_IDENTITIES_TOPIC))

        val records = consumer.poll(Duration.ofMillis(POLL_TIMEOUT_MS)).let { records ->
            records.mapNotNull { it.value() as? HostedIdentityEntry }
        }

        println("Read the following records from topic '${Schemas.P2P.P2P_HOSTED_IDENTITIES_TOPIC}': $records.")

        consumer.close()
        Thread.currentThread().contextClassLoader = contextCL
    }
}
