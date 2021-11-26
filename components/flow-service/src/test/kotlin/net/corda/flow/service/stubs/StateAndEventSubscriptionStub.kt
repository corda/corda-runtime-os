package net.corda.flow.service.stubs

import net.corda.data.flow.FlowInfo
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.Checkpoint
import net.corda.messaging.api.subscription.StateAndEventSubscription
import java.util.concurrent.CountDownLatch

class StateAndEventSubscriptionStub(private val startLatch: CountDownLatch,private val stopLatch: CountDownLatch) :
    StateAndEventSubscription<FlowInfo,
            Checkpoint,
        FlowEvent> {
    override val isRunning: Boolean
        get() = true

    override fun start() {
        startLatch.countDown()
    }

    override fun stop() {
        stopLatch.countDown()
    }
}
