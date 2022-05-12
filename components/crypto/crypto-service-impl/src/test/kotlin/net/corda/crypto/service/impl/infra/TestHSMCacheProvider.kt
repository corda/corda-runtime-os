package net.corda.crypto.service.impl.infra

import net.corda.crypto.persistence.hsm.HSMCache
import net.corda.crypto.persistence.hsm.HSMCacheProvider
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import org.mockito.kotlin.mock

class TestHSMCacheProvider(
    factory: TestServicesFactory,
    private val impl: HSMCacheProvider = mock()
) : HSMCacheProvider by impl {
    val coordinator = factory.coordinatorFactory.createCoordinator(
        LifecycleCoordinatorName.forComponent<HSMCacheProvider>()
    ) { e, c -> if(e is StartEvent) { c.updateStatus(LifecycleStatus.UP) } }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }

    override fun getInstance(): HSMCache = mock()
}