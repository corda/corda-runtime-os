package net.corda.messaging.api.mediator

/**
 * Class to store reply of the [MediatorProducer].
 */
data class ProducerReply(
    /** Reply message (set only if [MediatorProducer] supports request-reply messaging pattern). */
    val reply: MediatorMessage?,
    /** Exception (set in case of error). */
    val exception: Exception?,
)
