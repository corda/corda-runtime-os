package net.corda.p2p.linkmanager.integration.test.components

import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import kotlin.reflect.KClass

internal open class TestLifeCycle(
    coordinatorFactory: LifecycleCoordinatorFactory,
    cls: KClass<*>,
): Lifecycle {

    private val lifecycleCoordinator = coordinatorFactory.createCoordinator(
        LifecycleCoordinatorName(cls.java.name, null)
    ){ event, coordinator ->
        if(event is StartEvent) { coordinator.updateStatus(LifecycleStatus.UP) }
    }
    override val isRunning = true

    override fun start() {
        lifecycleCoordinator.start()
    }

    override fun stop() {
        lifecycleCoordinator.stop()
    }
}