package net.corda.messaging.api.mediator.config

import net.corda.messaging.api.mediator.MediatorProducer

/**
 * Class to store configuration for [MediatorProducer].
 *
 * @property onSerializationError Handler for serialization errors.
 */
class MediatorProducerConfig (
    val onSerializationError: (ByteArray) -> Unit,
)