package net.corda.libs.scheduler.datamodel

/**
 * Represents a lock on a given Task, allowing to find out when the last time was one was triggered.
 * This lock must be released by calling `updateAndRelease` or `release`.
 * Calling `close` is the equivalent of `release`.
 */
interface SchedulerLock : AutoCloseable {
    /**
     * Name of the task the log belongs to.
     */
    val taskName: String

    /**
     * Id of the scheduler taking the lock.
     */
    val schedulerId: String
    /**
     * Returns the time, in seconds, since the last time this task has been triggered
     */
    val secondsSinceLastScheduledTrigger: Long

    /**
     * Update the Scheduler Log with the current timestamp.
     * `schedulerId` is the name/id of the current process.
     */
    fun updateAndRelease(schedulerId: String)
}