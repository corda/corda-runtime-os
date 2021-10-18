package net.corda.messaging.api.subscription.listener

import net.corda.lifecycle.LifecycleStatus

/**
 * Client hooks that can be injected into any subscription.
 * This API allows for the client to be notified of all the lifecycle state changes instead of just polling the
 * [isRunning] boolean of the subscription
 */
fun interface LifecycleListener {

    /**
     * The implementation of this functional class will be used to notify you of any lifecycle events
     * @param LifecycleStatus the latest lifecycle status that occurred
     */
    fun onUpdate(lifecycleStatus: LifecycleStatus)
}