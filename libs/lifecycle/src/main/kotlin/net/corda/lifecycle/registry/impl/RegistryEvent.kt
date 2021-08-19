package net.corda.lifecycle.registry.impl

import net.corda.lifecycle.registry.CoordinatorStatus

/**
 * Events relating to coordinator status updates.
 *
 * These are posted to the status manager to be processed in its executor thread. As all events for the registry are
 * internal, they can be listed as a sealed class to take advantage of the "when" statement's exhaustiveness checking.
 */
sealed class RegistryEvent {

    /**
     * A new callback has been registered.
     *
     * @param callback The new callback.
     */
    data class NewCallback(val callback: CallbackRegistration) : RegistryEvent()

    /**
     * A callback has been cancelled.
     *
     * @param callback The callback to be removed from the list of callbacks.
     */
    data class CancelCallback(val callback: CallbackRegistration) : RegistryEvent()

    /**
     * The status of a coordinator should be updated.
     *
     * Note that this is also posted when a new coordinator is created.
     *
     * @param status The new coordinator status.
     */
    data class UpdateStatus(val status: CoordinatorStatus) : RegistryEvent()
}