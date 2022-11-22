package net.corda.crypto.hes.impl.infra

import net.corda.crypto.client.CryptoOpsClient
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.createCoordinator

class TestCryptoOpsClient(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val client: CryptoOpsClient
) : CryptoOpsClient by client {
    val lifecycleCoordinator = coordinatorFactory.createCoordinator<CryptoOpsClient>{ event, coordinator ->
        if(event is StartEvent) { coordinator.updateStatus(LifecycleStatus.UP) }
    }

    override val isRunning: Boolean
        get() = lifecycleCoordinator.isRunning

    override fun start() {
        lifecycleCoordinator.start()
    }

    override fun stop() {
        lifecycleCoordinator.stop()
    }
}