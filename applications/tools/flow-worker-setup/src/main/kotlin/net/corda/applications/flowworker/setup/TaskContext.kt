package net.corda.applications.flowworker.setup

import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.configuration.MessagingConfig.Boot.INSTANCE_ID
import net.corda.schema.configuration.MessagingConfig.Boot.TOPIC_PREFIX
import net.corda.schema.configuration.MessagingConfig.Bus.BOOTSTRAP_SERVER
import net.corda.schema.configuration.MessagingConfig.Bus.BUS_TYPE
import net.corda.v5.base.concurrent.getOrThrow
import org.apache.kafka.clients.CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG
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
        val bootConfig = SmartConfigImpl.empty()
            .withValue(INSTANCE_ID, ConfigValueFactory.fromAnyRef(startArgs.instanceId))
            .withValue(TOPIC_PREFIX, ConfigValueFactory.fromAnyRef(startArgs.topicPrefix))
            .withValue(BOOTSTRAP_SERVER, ConfigValueFactory.fromAnyRef(startArgs.bootstrapServer))
            .withValue(BUS_TYPE, ConfigValueFactory.fromAnyRef("KAFKA"))
        publisher = publisherFactory.createPublisher(PublisherConfig("Flow Worker Setup", false), bootConfig)
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
        kafkaProperties[BOOTSTRAP_SERVERS_CONFIG] = startArgs.bootstrapServer
        return kafkaProperties
    }
}