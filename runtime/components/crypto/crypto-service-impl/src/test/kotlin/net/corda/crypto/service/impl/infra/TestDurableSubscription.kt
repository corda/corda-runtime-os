package net.corda.crypto.service.impl.infra

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.messaging.api.subscription.Subscription
import org.slf4j.LoggerFactory

class TestDurableSubscription<K, V>(
    coordinatorFactory: LifecycleCoordinatorFactory,
    override val subscriptionName: LifecycleCoordinatorName =
        LifecycleCoordinatorName("TestDurableSubscription"),
) : Subscription<K, V> {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }


    val lifecycleCoordinator = coordinatorFactory.createCoordinator(subscriptionName) { event, coordinator ->
        logger.info("LifecycleEvent received: $event")
        if(event is StartEvent) { coordinator.updateStatus(LifecycleStatus.UP) }
    }

    override val isRunning: Boolean
        get() = lifecycleCoordinator.isRunning

    override fun start() {
        lifecycleCoordinator.start()
    }

    override fun close() {
        lifecycleCoordinator.close()
    }
}
