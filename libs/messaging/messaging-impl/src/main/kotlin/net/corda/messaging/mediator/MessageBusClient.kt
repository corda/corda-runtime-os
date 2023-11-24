package net.corda.messaging.mediator

import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.mediator.MessagingClient
import net.corda.messaging.api.mediator.MessagingClient.Companion.MSG_PROP_ENDPOINT
import net.corda.messaging.api.mediator.MessagingClient.Companion.MSG_PROP_KEY
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture

class MessageBusClient(
    override val id: String,
    private val producer: CordaProducer,
) : MessagingClient {

    private companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun send(message: MediatorMessage<*>): MediatorMessage<*> {
        val future = CompletableFuture<Unit>()
        val record = message.toCordaProducerRecord()
        producer.send(record) { ex ->
            setFutureFromResponse(ex, future, record.topic)
        }

        return MediatorMessage(future)
    }

    /**
     * Helper function to set a [future] result based on the presence of an [exception]
     */
    private fun setFutureFromResponse(
        exception: Exception?,
        future: CompletableFuture<Unit>,
        topic: String
    ) {
        if (exception == null) {
            future.complete(Unit)
        } else {
            val message = "Producer clientId $id for topic $topic failed to send."
            log.warn(message, exception)
            future.completeExceptionally(CordaMessageAPIFatalException(message, exception))
        }
    }

    override fun close() {
        try {
            producer.close()
        } catch (ex: Exception) {
            log.info(
                "Failed to close message bus messaging client [$id] safely.", ex
            )
        }
    }
}

/**
 * Helper function to convert a [MediatorMessage] of a specific format to a [CordaProducerRecord]
 */
private fun MediatorMessage<*>.toCordaProducerRecord(): CordaProducerRecord<*, *> {
    return CordaProducerRecord(
        topic = this.getProperty<String>(MSG_PROP_ENDPOINT),
        key = this.getProperty(MSG_PROP_KEY),
        value = this.payload,
        headers = this.properties.toHeaders(),
    )
}

/**
 * Helper function to extract headers from message props
 */
private fun Map<String, Any>.toHeaders() =
    map { (key, value) -> (key to value.toString()) }