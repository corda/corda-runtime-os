package net.corda.messaging.api.mediator.config

import net.corda.messaging.api.mediator.MessagingClient

/**
 * Class to store configuration for [MessagingClient].
 *
 * @property onSerializationError Handler for serialization errors.
 */
class MessagingClientConfig (
    val onSerializationError: (ByteArray) -> Unit,
)