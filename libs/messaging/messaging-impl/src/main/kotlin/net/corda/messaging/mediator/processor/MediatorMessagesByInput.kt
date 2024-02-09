package net.corda.messaging.mediator.processor

import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.api.records.Record

/**
 * Class to represent replayed consumer inputs and their outputs
 */
data class MediatorMessagesByInput<K: Any, V: Any>  (
    val inputRecord: Record<K, V>,
    val outputs: List<MediatorMessage<Any>>
)
