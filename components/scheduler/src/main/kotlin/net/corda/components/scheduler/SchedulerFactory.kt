package net.corda.components.scheduler

/**
 * Creates a `Scheduler` for the given `Schedule`.
 */
interface SchedulerFactory {
    fun create(schedule: Schedule): Scheduler
}