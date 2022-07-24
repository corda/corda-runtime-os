package net.corda.crypto.persistence.impl.tests.infra

import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.reconciliation.VersionedRecord
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoListener
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Instant
import java.util.UUID
import java.util.stream.Stream

@Component
class TestVirtualNodeInfoReadService @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory
) : VirtualNodeInfoReadService {
    override val lifecycleCoordinatorName: LifecycleCoordinatorName =
        LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>()

    val lifecycleCoordinator = coordinatorFactory.createCoordinator(lifecycleCoordinatorName) { e, c ->
        if (e is StartEvent) {
            c.updateStatus(LifecycleStatus.UP)
        }
    }

    override val isRunning: Boolean
        get() = lifecycleCoordinator.isRunning

    override fun start() {
        lifecycleCoordinator.start()
    }

    override fun stop() {
        lifecycleCoordinator.stop()
    }

    override fun getAll(): List<VirtualNodeInfo> =
        throw NotImplementedError()

    override fun get(holdingIdentity: HoldingIdentity): VirtualNodeInfo = VirtualNodeInfo(
        holdingIdentity = HoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB", "group"),
        cpiIdentifier = CpiIdentifier(
            name = "some-name",
            version = "1",
            signerSummaryHash = null
        ),
        vaultDmlConnectionId = UUID.randomUUID(),
        cryptoDmlConnectionId = UUID.randomUUID(),
        timestamp = Instant.now()
    )

    override fun getById(id: String): VirtualNodeInfo? =
        throw NotImplementedError()

    override fun registerCallback(listener: VirtualNodeInfoListener): AutoCloseable =
        throw NotImplementedError()

    override fun getAllVersionedRecords(): Stream<VersionedRecord<HoldingIdentity, VirtualNodeInfo>> =
        throw NotImplementedError()
}