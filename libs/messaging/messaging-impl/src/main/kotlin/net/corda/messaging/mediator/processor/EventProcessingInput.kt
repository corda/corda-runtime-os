package net.corda.messaging.mediator.processor

import net.corda.libs.statemanager.api.State
import net.corda.messagebus.api.consumer.CordaConsumerRecord

data class EventProcessingInput<K: Any, E: Any>(
    val key: K,
    val records: List<CordaConsumerRecord<K, E>>,
    val state: State?
)