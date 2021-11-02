package net.corda.applications.examples.persistence.publisher

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.v5.base.util.contextLogger
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture

class KafkaPublisher(
    private val smartConfigFactory: SmartConfigFactory,
    private val brokerAddress: String,
    publisherFactory: PublisherFactory
) {
    private companion object {
        val log: Logger = contextLogger()
        val consoleLogger: Logger = LoggerFactory.getLogger("Console")
    }

    private val pubConfig by lazy {
        PublisherConfig(KafkaPublisher::class.java.name)
    }

    private val config by lazy {
        smartConfigFactory.create(ConfigFactory.parseMap(mapOf(
         ConfigConstants.KAFKA_BOOTSTRAP_SERVER to brokerAddress,
         ConfigConstants.TOPIC_PREFIX_CONFIG_KEY to ConfigConstants.TOPIC_PREFIX
        )))
    }

    private val publisher by lazy {
        log.info("pubConfig: $pubConfig")
        log.info("kafkaConfig: $config")
        publisherFactory.createPublisher(pubConfig, config)
    }

    fun publish(topic: String, key: String, message: Any): CompletableFuture<Unit> {
        return publisher.publish(listOf(Record(topic, key, message))).get(0)
    }
}