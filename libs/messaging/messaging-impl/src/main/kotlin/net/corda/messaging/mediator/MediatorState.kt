package net.corda.messaging.mediator

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Class to store shared state tracked by the mediator patterns components
 * @property stopped When set to true, new records are not processed by the mediator
 * @property running When set to true, the mediator pattern has been started. When False the mediator pattern is closed or errorred.
 */
data class MediatorState (
    private val stopped: AtomicBoolean,
    val running: AtomicBoolean
    ) {
    fun stop() = stopped.set(true)
    fun stopped() = stopped.get()
    fun running() = running.get()
}
