package net.corda.lifecycle.domino.logic.util

import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.messaging.api.subscription.Subscription

class EventLogSubscriptionWithDominoLogic<K, V>(
    private val eventLogSubscription: Subscription<K, V>,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory
): Lifecycle {

    override val isRunning: Boolean
        get() = dominoTile.isRunning

    val dominoTile = DominoTile(this::class.java.simpleName, lifecycleCoordinatorFactory, ::createResources)

    fun createResources(resources: ResourcesHolder) {
        eventLogSubscription.start()
        resources.keep {
            eventLogSubscription.stop()
        }
    }

    override fun start() {
        if (!isRunning) {
            dominoTile.start()
        }
    }

    override fun stop() {
        if (isRunning) {
            dominoTile.stop()
        }
    }
}
