package net.corda.messaging.impl.publisher

import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.records.Record
import net.corda.messaging.impl.subscription.subscriptions.pubsub.service.TopicService
import net.corda.v5.base.concurrent.CordaFuture
import net.corda.v5.base.internal.concurrent.openFuture
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class CordaPublisher<K : Any, V : Any> (
    publisherConfig: PublisherConfig,
    private val topicService: TopicService
    ) : Publisher<K, V> {

    private companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    private val instanceId = publisherConfig.instanceId
    private val clientId = publisherConfig.clientId
    private val topic = publisherConfig.topic

    @Suppress("TooGenericExceptionCaught")
    override fun publish(record: Record<K, V>): CordaFuture<Boolean> {
        val fut = openFuture<Boolean>()

        try {
            topicService.addRecords(listOf(record))
            fut.set(true)
        } catch (ex: Exception) {
            val message = "Corda publisher clientId $clientId, instanceId $instanceId, " +
            "for topic $topic failed to send record."
            log.error(message, ex)
            fut.setException(CordaMessageAPIFatalException(message, ex))
        }

        return fut
    }

    override fun close() {
        log.info("Closing Corda publisher clientId $clientId, instanceId $instanceId, " +
                "for topic $topic.")
    }
}
