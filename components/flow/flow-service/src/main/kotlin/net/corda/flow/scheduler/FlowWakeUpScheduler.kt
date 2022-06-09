package net.corda.flow.scheduler

import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.state.Checkpoint
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.subscription.listener.StateAndEventListener

/**
 * The [FlowWakeUpScheduler] is listens to [Checkpoint] state changes and schedules periodic [Wakeup] events based
 * on the [Checkpoint] maxFlowSleepDuration.
 */
interface FlowWakeUpScheduler : StateAndEventListener<String, Checkpoint> {

    /**
     * Called when the worker configuration changes, the scheduler uses the messaging configuration section
     * when publishing the scheduled wakeup events.
     *
     * @param config map of the worker's configuration sections
     */
    fun onConfigChange(config: Map<String, SmartConfig>)
}