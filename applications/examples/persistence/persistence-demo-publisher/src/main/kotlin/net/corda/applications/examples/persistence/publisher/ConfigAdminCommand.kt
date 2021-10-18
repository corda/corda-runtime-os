package net.corda.applications.examples.persistence.publisher

import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.osgi.api.Shutdown
import picocli.CommandLine
import java.util.UUID

@CommandLine.Command(
    name = "config-admin",
    mixinStandardHelpOptions = true,
    description = ["Publish a config admin message"])
class ConfigAdminCommand(
    private val publisherFactory: PublisherFactory,
    shutDownService: Shutdown
) : CommandBase(shutDownService), Runnable {
    companion object {
        const val TOPIC_NAME = "config-event"
    }
    override fun run() {
        call {
            println("Publishing config-admin event to $kafka/${ConfigConstants.TOPIC_PREFIX}$TOPIC_NAME")
            val publisher = KafkaPublisher(kafka, publisherFactory)
            val key = UUID.randomUUID().toString()
            val msg = "foo"
            publisher
                .publish(TOPIC_NAME, key, msg)
                .get()
            println("Published: $msg with key $key")
        }
    }
}

