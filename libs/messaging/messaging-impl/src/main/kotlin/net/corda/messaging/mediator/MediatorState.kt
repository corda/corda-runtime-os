package net.corda.messaging.mediator

import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.mediator.processor.StatesToPersist
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class MediatorState (
    private val stopped: AtomicBoolean,
    val running: AtomicBoolean,
    val asynchronousOutputs: ConcurrentHashMap<String, MutableList<MediatorMessage<Any>>>,
    val statesToPersist: StatesToPersist
    ) {
    fun stop() = stopped.set(true)
    fun stopped() = stopped.get()
    fun running() = running.get()

    fun clear() {
        asynchronousOutputs.clear()
        statesToPersist.clear()
    }
}
