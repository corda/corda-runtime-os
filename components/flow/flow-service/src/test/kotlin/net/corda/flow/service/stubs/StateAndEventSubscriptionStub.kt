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

    private var _isRunning = false
    override val isRunning: Boolean
        get() = _isRunning

    override fun start() {
        _isRunning = true
    }

    override fun stop() {
        _isRunning = false
    }

    override val subscriptionName: LifecycleCoordinatorName
        get() = LifecycleCoordinatorName("StateAndEventSubscriptionStub")
}
