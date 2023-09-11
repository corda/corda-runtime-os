package net.corda.libs.scheduler.datamodel

/**
 * API to the Scheduler Log.
 * This allows to find out when a particular task was last triggered and take a lock on triggering the task.
 */
interface SchedulerLog {
    /**
     * Get a `SchedulerLock` object for the given task.
     * @param taskName name of the task to retrieve the scheduler for
     * @param schedulerId id of this scheduler process
     */
    fun getLastTriggerAndLock(taskName: String, schedulerId: String) : SchedulerLock
}

