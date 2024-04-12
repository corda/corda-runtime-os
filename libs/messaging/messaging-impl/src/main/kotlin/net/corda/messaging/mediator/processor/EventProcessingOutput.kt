package net.corda.messaging.mediator.processor

import net.corda.libs.statemanager.api.State
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messaging.api.mediator.MediatorMessage

data class  EventProcessingOutput<K: Any, V:Any>(
    val asyncOutputs: List<MediatorMessage<Any>>,
    val stateChangeAndOperation: StateChangeAndOperation,
    val processedOffsets: List<CordaConsumerRecord<K, V>>
)

sealed interface StateChangeAndOperation {
    val outputState: State?

    class Create(override val outputState: State) : StateChangeAndOperation
    class Update(override val outputState: State) : StateChangeAndOperation
    class Delete(override val outputState: State) : StateChangeAndOperation

    // This can happen if both input and output are null. There may still be outputs in this case (for example a flow
    // status change).
    object Noop : StateChangeAndOperation {
        override val outputState: State? = null
    }

    // This represents a transient error in event processing, and acts as a signal back to the poll loop that a retry
    // should occur.
    object Transient : StateChangeAndOperation {
        override val outputState: State? = null
    }
}
