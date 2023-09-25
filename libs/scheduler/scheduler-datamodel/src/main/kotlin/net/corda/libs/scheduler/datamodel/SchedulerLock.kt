package net.corda.libs.scheduler.datamodel

/**
 * Represents a lock on a given Task, allowing to find out when the last time was one was triggered.
 * This lock must be released by closing the `ScheduleLock`.
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
     * Updates the scheduler log entry with the current timestamp.
     *
     * @param schedulerId is the name/id of the current process.
     */
    fun updateLog(schedulerId: String)

    /**
     * Releases the log entry in the database.
     */
    override fun close()
}