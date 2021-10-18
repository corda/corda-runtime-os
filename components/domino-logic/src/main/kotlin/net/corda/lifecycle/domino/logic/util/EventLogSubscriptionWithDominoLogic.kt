package net.corda.lifecycle.domino.logic.util

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.messaging.api.subscription.Subscription

class EventLogSubscriptionWithDominoLogic<K, V>(
    private val eventLogSubscription: Subscription<K, V>,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory
) :
    DominoTile(lifecycleCoordinatorFactory) {

    override fun createResources() {
        eventLogSubscription.start()
        resources.keep {
            eventLogSubscription.stop()
        }
    }
}
