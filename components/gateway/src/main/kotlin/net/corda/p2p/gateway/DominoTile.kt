package net.corda.p2p.gateway

import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleStatus

abstract class DominoTile(
    private val coordinator: LifecycleCoordinator
) : Lifecycle {
    override val isRunning
        get() = coordinator.status == LifecycleStatus.UP

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }
}
