package net.corda.flow.service.stubs

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.Checkpoint
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.messaging.api.subscription.StateAndEventSubscription

class StateAndEventSubscriptionStub :
    StateAndEventSubscription<FlowKey,
            Checkpoint,
            FlowEvent> {

    var isStarted = false

    override val isRunning: Boolean
        get() = true

    override fun start() {
        isStarted = true
    }

    override fun stop() {
        isStarted = false
    }

    override val subscriptionName: LifecycleCoordinatorName
        get() = LifecycleCoordinatorName("StateAndEventSubscriptionStub")
}
