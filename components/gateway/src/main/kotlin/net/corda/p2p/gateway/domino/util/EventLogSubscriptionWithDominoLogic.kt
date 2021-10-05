package net.corda.p2p.gateway.domino.util

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.subscription.Subscription
import net.corda.p2p.gateway.domino.LeafTile

class EventLogSubscriptionWithDominoLogic<K, V>(
    private val eventLogSubscription: Subscription<K, V>,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory
) :
    LeafTile(lifecycleCoordinatorFactory) {

    override fun createResources() {
        eventLogSubscription.start()
        executeBeforeStop {
            eventLogSubscription.stop()
        }
        updateState(State.Started)
    }
}
