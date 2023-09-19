package net.corda.messaging.mediator

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.mediator.MediatorProducer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class MessageBusProducer(
    override val id: String,
    private val producer: CordaProducer,
) : MediatorProducer {

    private companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun send(message: MediatorMessage<*>, endpoint: String): Deferred<MediatorMessage<*>?> =
        CompletableDeferred<MediatorMessage<*>?>().apply {
            producer.send(message.toCordaProducerRecord(endpoint)) { ex ->
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
                "Failed to close producer [$id] safely.", ex
            )
        }
    }
}

private fun MediatorMessage<*>.toCordaProducerRecord(endpoint: String) : CordaProducerRecord<*, *> {
    return CordaProducerRecord(
        topic = endpoint,
        key = this.getProperty("key"),
        value = this.payload,
        headers = this.getProperty<Headers>("headers"),
    )
}

private typealias Headers = List<Pair<String, String>>