package net.corda.components.scheduler

import net.corda.lifecycle.Lifecycle

/**
 * Marker interface for a Scheduler, which implementation will be responsible for triggering
 * tasks at a schedule define in `Schedule`.
 */
interface Scheduler : Lifecycle {
    val schedule: Schedule
}