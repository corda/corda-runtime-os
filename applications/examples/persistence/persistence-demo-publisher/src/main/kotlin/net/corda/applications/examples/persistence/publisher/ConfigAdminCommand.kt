package net.corda.applications.examples.persistence.publisher

import net.corda.data.poc.persistence.ConfigAdminEvent
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.osgi.api.Shutdown
import picocli.CommandLine
import java.util.UUID

@CommandLine.Command(
    name = "config-admin",
    mixinStandardHelpOptions = true,
    description = ["Publish a config admin message"])
class ConfigAdminCommand(
    private val smartConfigFactory: SmartConfigFactory,
    private val publisherFactory: PublisherFactory,
    shutDownService: Shutdown
) : CommandBase(shutDownService), Runnable {
    companion object {
        const val TOPIC_NAME = "config-event"
    }

    @CommandLine.Option(
        names = ["-c", "--config-key"],
        paramLabel = "KEY",
        description = ["Config Key"],
        required = true
    )
    var key: String? = null

    @CommandLine.Option(
        names = ["-d", "--config-value"],
        paramLabel = "VALUE",
        description = ["Config Value"],
        required = true
    )
    var value: String? = null

    @CommandLine.Option(
        names = ["-e", "--config-version"],
        paramLabel = "VERSION",
        description = ["Config Value Version - default to 1"],
    )
    var version: Int = 1

    override fun run() {
        call {
            println("Publishing config-admin event to $kafka/${ConfigConstants.TOPIC_PREFIX}$TOPIC_NAME")
            val publisher = KafkaPublisher(smartConfigFactory, kafka, publisherFactory)
            val eventKey = UUID.randomUUID().toString()
            val msg = ConfigAdminEvent(key, value, version)
            publisher
                .publish(TOPIC_NAME, eventKey, msg)
                .get()
            println("Published: $msg with key $key")
        }
    }
}

