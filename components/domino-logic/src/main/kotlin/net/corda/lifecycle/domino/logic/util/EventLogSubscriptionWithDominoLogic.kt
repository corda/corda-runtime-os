package net.corda.lifecycle.domino.logic.util

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.LeafTile
import net.corda.messaging.api.subscription.Subscription

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
