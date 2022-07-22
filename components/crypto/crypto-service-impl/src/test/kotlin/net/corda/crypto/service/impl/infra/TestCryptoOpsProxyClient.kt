package net.corda.crypto.service.impl.infra

import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.client.CryptoOpsProxyClient
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import org.mockito.kotlin.mock

class TestCryptoOpsProxyClient(
    private val factory: TestServicesFactory,
    private val impl: CryptoOpsProxyClient = mock()
) : CryptoOpsProxyClient by impl {
    val coordinator = factory.coordinatorFactory.createCoordinator(
        LifecycleCoordinatorName.forComponent<CryptoOpsClient>()
    ) { e, c -> if(e is StartEvent) { c.updateStatus(LifecycleStatus.UP) } }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }

    override fun createWrappingKey(
        workerSetId: String,
        failIfExists: Boolean,
        masterKeyAlias: String,
        context: Map<String, String>
    ) {
        factory.cryptoService.createWrappingKey(masterKeyAlias, failIfExists, context)
    }
}