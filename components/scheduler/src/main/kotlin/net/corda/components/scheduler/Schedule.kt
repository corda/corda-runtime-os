package net.corda.components.scheduler

/**
 * Define a schedule for a given task.
 */
data class Schedule(
    /**
     * Name of the task to be scheduled.
     */
    val taskName: String,
    /**
     * Interval at which the task needs to be scheduled.
     */
    // NOTE: currently we support interval in seconds.
    // We could later extend this to interval based on fixed time such as "on the hour", similar to
    // cron expressions. This can be added when a use case emerges.
    val scheduleIntervalInSeconds: Long,
    /**
     * Topic to which the trigger should be published.
     */
    val scheduleTriggerTopic: String,
)