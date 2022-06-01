package net.corda.crypto.service.impl.infra

import net.corda.crypto.persistence.hsm.HSMStore
import net.corda.crypto.persistence.hsm.HSMStoreProvider
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import org.mockito.kotlin.mock

class TestHSMStoreProvider(
    factory: TestServicesFactory,
    private val impl: HSMStoreProvider = mock()
) : HSMStoreProvider by impl {
    val coordinator = factory.coordinatorFactory.createCoordinator(
        LifecycleCoordinatorName.forComponent<HSMStoreProvider>()
    ) { e, c -> if(e is StartEvent) { c.updateStatus(LifecycleStatus.UP) } }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }

    override fun getInstance(): HSMStore = TestHSMStore()
}