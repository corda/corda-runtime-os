package net.corda.applications.examples.persistence.publisher

import net.corda.data.poc.persistence.AdminEventType
import net.corda.data.poc.persistence.ClusterAdminEvent
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.osgi.api.Shutdown
import picocli.CommandLine
import java.util.UUID

@CommandLine.Command(
    name = "cluster-admin",
    mixinStandardHelpOptions = true,
    description = ["Publish a cluster admin message"])
class ClusterAdminCommand(
    private val smartConfigFactory: SmartConfigFactory,
    private val publisherFactory: PublisherFactory,
    shutDownService: Shutdown
) : CommandBase(shutDownService), Runnable {
    companion object {
        const val TOPIC_NAME = "cluster-admin-event"
    }
    override fun run() {
        call {
            println("Publishing cluster-admin event to $kafka/${ConfigConstants.TOPIC_PREFIX}$TOPIC_NAME")
            val publisher = KafkaPublisher(smartConfigFactory, kafka, publisherFactory)
            val key = UUID.randomUUID().toString()
            val msg = ClusterAdminEvent(AdminEventType.DB_SCHEMA_UPDATE)
            publisher
                .publish("cluster-admin-event", key, msg)
                .get()
            println("Published: $msg with key $key")
        }
    }
}

