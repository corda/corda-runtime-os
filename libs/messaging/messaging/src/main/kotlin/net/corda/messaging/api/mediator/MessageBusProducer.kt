package net.corda.messaging.api.mediator

import net.corda.messagebus.api.producer.CordaProducer

/**
 * Message bus producer that sends messages to message bus topics.
 */
class MessageBusProducer(
    override val name: String,
    private val producer: CordaProducer,
): MediatorProducer {

    override fun send(message: Message, address: String): ProducerReply {
        TODO("Not implemented yet")
    }

    override fun close() =
        producer.close()
}