package net.corda.messaging.mediator

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.mediator.MessagingClient
import net.corda.messaging.api.mediator.MessagingClient.Companion.MSG_PROP_ENDPOINT
import net.corda.messaging.api.mediator.MessagingClient.Companion.MSG_PROP_KEY
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class MessageBusClient(
    override val id: String,
    private val producer: CordaProducer,
) : MessagingClient {

    private companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun send(message: MediatorMessage<*>): Deferred<MediatorMessage<*>?> =
        CompletableDeferred<MediatorMessage<*>?>().apply {
            producer.send(message.toCordaProducerRecord()) { ex ->
                if (ex != null) {
                    completeExceptionally(ex)
                } else {
                    complete(null)
                }
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

private fun MediatorMessage<*>.toCordaProducerRecord(): CordaProducerRecord<*, *> {
    return CordaProducerRecord(
        topic = this.getProperty<String>(MSG_PROP_ENDPOINT),
        key = this.getProperty(MSG_PROP_KEY),
        value = this.payload,
        headers = this.properties.toHeaders(),
    )
}

private fun Map<String, Any>.toHeaders() =
    map { (key, value) -> (key to value.toString()) }