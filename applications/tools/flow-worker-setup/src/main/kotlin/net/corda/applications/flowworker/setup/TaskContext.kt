package net.corda.applications.flowworker.setup

import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.v5.base.concurrent.getOrThrow
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import java.util.*
import java.util.concurrent.TimeUnit

class TaskContext(
    val startArgs: Args,
    publisherFactory: PublisherFactory
) {

    private val kafkaAdminClient: AdminClient
    private val publisher: Publisher

    init {
        kafkaAdminClient = AdminClient.create(getKafkaProperties())
        publisher = publisherFactory.createPublisher(PublisherConfig("Flow Worker Setup"), SmartConfigImpl.empty())
    }

    fun createTopics(topics: List<NewTopic>) {
        /*
        * sorry for the hack, need to find a more deterministic way to detect the topics
        * have been deleted
        */
        var retry = 10
        while (retry > 0) {
            try {
                kafkaAdminClient.createTopics(topics).all().get(10, TimeUnit.SECONDS)
                return
            } catch (e: Throwable) {
                retry--
                Thread.sleep(250)
            }
        }
    }

    fun deleteAllTopics() {
        val topicsToDelete = getTopics()

        kafkaAdminClient.deleteTopics(topicsToDelete).all().get(10, TimeUnit.SECONDS)
    }

    private fun getTopics(): List<String> {
        return kafkaAdminClient
            .listTopics()
            .listings()
            .get(10, TimeUnit.SECONDS)
            .map { it.name() }
            .toList()
    }

    fun <K : Any, V : Any> publish(record: Record<K, V>) {
        publish(listOf(record))
    }

    fun publish(records: List<Record<*, *>>) {
        publisher.publish(records).forEach { it.getOrThrow() }
    }


    private fun getKafkaProperties(): Properties {
        val kafkaProperties = Properties()
        // Hacked for now, need to improve
        kafkaProperties["bootstrap.servers"] = "localhost:9092"
        return kafkaProperties
    }
}