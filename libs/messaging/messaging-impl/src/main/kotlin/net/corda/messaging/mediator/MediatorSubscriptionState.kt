package net.corda.messaging.mediator

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Class to store shared state tracked by the mediator patterns components
 * @property stopped When set to true, new records are not processed by the mediator. When set to false the consumers will poll and
 * process records.
 * @property running When set to true, the mediator pattern has been started. When False the mediator pattern is closed or errorred.
 */
data class MediatorSubscriptionState (
    private val stopped: AtomicBoolean = AtomicBoolean(false),
    val running: AtomicBoolean = AtomicBoolean(false)
    ) {
    fun stop() = stopped.set(true)
    fun stopped() = stopped.get()
    fun running() = running.get()
}
