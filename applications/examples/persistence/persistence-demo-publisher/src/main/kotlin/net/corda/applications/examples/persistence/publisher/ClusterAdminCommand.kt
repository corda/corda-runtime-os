package net.corda.applications.examples.persistence.publisher

import net.corda.data.poc.persistence.AdminEventType
import net.corda.data.poc.persistence.ClusterAdminEvent
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.osgi.api.Shutdown
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.util.UUID

@CommandLine.Command(
    name = "cluster-admin",
    mixinStandardHelpOptions = true,
    description = ["Publish a cluster admin message"])
class ClusterAdminCommand(
    private val publisherFactory: PublisherFactory,
    shutDownService: Shutdown
) : CommandBase(shutDownService), Runnable {
    override fun run() {
        call {
            println("Publishing cluster-admin event to ${kafka}")
            val publisher = KafkaPublisher(kafka, publisherFactory)
            val key = UUID.randomUUID().toString()
            val msg = ClusterAdminEvent(AdminEventType.DB_SCHEMA_UPDATE)
            publisher
                .publish("cluster-admin-event", key, msg)
                .get()
            println("Published: $msg with key $key")
        }
    }
}

