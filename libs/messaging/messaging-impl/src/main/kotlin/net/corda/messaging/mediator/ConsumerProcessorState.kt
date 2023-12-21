package net.corda.messaging.mediator

import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.mediator.processor.StatesToPersist
import java.util.concurrent.ConcurrentHashMap

/**
 * Class to store shared state tracked by the mediator patterns components
 * @property stopped When set to true, new records are not processed by the mediator
 * @property running When set to true, the mediator pattern has been started. When False the mediator pattern is closed or errorred.
 * @property asynchronousOutputs The asynchronous outputs associated with a
 */
data class ConsumerProcessorState (
    val asynchronousOutputs: ConcurrentHashMap<String, MutableList<MediatorMessage<Any>>> = ConcurrentHashMap<String,
            MutableList<MediatorMessage<Any>>>(),
    val statesToPersist: StatesToPersist = StatesToPersist()
    ) {
    fun clear() {
        asynchronousOutputs.clear()
        statesToPersist.clear()
    }
}
