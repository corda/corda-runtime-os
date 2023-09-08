package net.corda.components.scheduler

import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorName

interface TriggerPublisher: Lifecycle {
    val lifecycleCoordinatorName: LifecycleCoordinatorName

    /**
     * Publishes a task trigger to the message bus.
     * @param taskName name of the task to trigger
     * @param topicName name of the topic to publish the trigger to
     */
    fun publish(taskName: String, topicName: String)
}