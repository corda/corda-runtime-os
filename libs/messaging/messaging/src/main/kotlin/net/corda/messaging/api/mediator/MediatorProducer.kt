package net.corda.messaging.api.mediator

/**
 * Multi-source event mediator message producer.
 */
interface MediatorProducer: AutoCloseable {

    /**
     * Determines whether producer supports request-reply messaging pattern.
     */
    val isRequestReply: Boolean
        get() = false

    /**
     * Sends message to producer's endpoint and returns reply.
     *
     * @param message Message
     * @returns ProducerReply Holds producer's reply if producer supports request-reply messaging pattern
     *   (@see [isRequestReply]).
     */
    fun send(message: Message): ProducerReply
}