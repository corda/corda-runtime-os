package net.corda.messaging.api.mediator.config

import net.corda.messaging.api.mediator.MediatorConsumer

/**
 * Class to store configuration for [MediatorConsumer].
 *
 * @property keyClass Class of the message key.
 * @property valueClass Class of the message value (payload).
 * @property onSerializationError Handler for serialization errors.
 */
class MediatorConsumerConfig<K, V> (
    val keyClass: Class<K>,
    val valueClass: Class<V>,
    val onSerializationError: (ByteArray) -> Unit,
)