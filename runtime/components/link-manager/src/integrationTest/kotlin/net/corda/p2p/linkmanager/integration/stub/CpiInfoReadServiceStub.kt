package net.corda.p2p.linkmanager.integration.stub

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.lifecycle.LifecycleCoordinatorName

class CpiInfoReadServiceStub : CpiInfoReadService {
    override fun getAll() = throw UnsupportedOperationException()

    override fun get(identifier: CpiIdentifier) = throw UnsupportedOperationException()

    override fun getAllVersionedRecords() = throw UnsupportedOperationException()

    override val lifecycleCoordinatorName = LifecycleCoordinatorName.forComponent<CpiInfoReadService>()

    override val isRunning = true

    override fun start() = Unit

    override fun stop() = Unit
}
