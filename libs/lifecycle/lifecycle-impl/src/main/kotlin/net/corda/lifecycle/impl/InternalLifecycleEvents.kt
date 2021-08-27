package net.corda.lifecycle.impl

import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.TimerEvent

/**
 * Purely internal events for the lifecycle coordinator to process. These are used to ensure that state changes to the
 * coordinator always happen on an executor thread. As there should only ever be a single thread processing events at
 * any one time, this ensures there is no race condition in updating coordinator state.
 */

/**
 * Create a new timer.
 *
 * This takes the parameters of the `createTimer` function on the API and packages them for processing elsewhere.
 */
internal data class SetUpTimer(
    val key: String,
    val delay: Long,
    val timerEventGenerator: (String) -> TimerEvent
) : LifecycleEvent

/**
 * Cancel a timer.
 */
internal data class CancelTimer(val key: String) : LifecycleEvent

/**
 * Add a new registration for this coordinator to update with status changes.
 *
 * @param registration The new registration to keep updated
 */
internal data class NewRegistration(val registration: Registration) : LifecycleEvent

/**
 * Remove a registration for this coordinator from the set to update with status changes.
 *
 * @param registration The registration to remove from the set to keep updated.
 */
internal data class CancelRegistration(val registration: Registration) : LifecycleEvent

/**
 * Keep track of a registration this coordinator now has on some dependents.
 *
 * This is primarily used to update the coordinator of the current registration status on startup.
 *
 * @param registration The registration to track.
 */
internal data class TrackRegistration(val registration: Registration) : LifecycleEvent

/**
 * Stop tracking a registration this coordinator had on some dependents.
 *
 * This is used to clean up registration tracking state when a registration is cancelled.
 *
 * @param registration The registration to stop tracking.
 */
internal data class StopTrackingRegistration(val registration: Registration) : LifecycleEvent

/**
 * Indicates that the component has changed status, so this component can inform dependent components of the change.
 *
 * @param newStatus The new status this component has taken.
 * @param reason The reason this status was entered, for diagnostic purposes
 */
internal data class StatusChange(val newStatus: LifecycleStatus, val reason: String) : LifecycleEvent

/**
 * Perform any state cleanup for the coordinator as it closes.
 */
internal class CloseCoordinator : LifecycleEvent
