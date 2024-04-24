package net.corda.session.mapper.service.integration

import net.corda.data.p2p.HostedIdentityEntry
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.membership.locally.hosted.identities.IdentityInfo
import net.corda.membership.locally.hosted.identities.LocallyHostedIdentitiesService
import net.corda.reconciliation.VersionedRecord
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.stream.Stream

@Component(service = [LocallyHostedIdentitiesService::class])
class DummyLocallyHostedIdentitiesService @Activate constructor(@Reference(service = LifecycleCoordinatorFactory::class)
                                                                    private val coordinatorFactory: LifecycleCoordinatorFactory,
) : LocallyHostedIdentitiesService {
    private val coordinator =
        coordinatorFactory.createCoordinator(
            LifecycleCoordinatorName.forComponent<LocallyHostedIdentitiesService>()
        ) { event, coordinator ->
            if (event is StartEvent) {
                coordinator.updateStatus(LifecycleStatus.UP)
            }
        }

    private val identityMap = mutableMapOf<HoldingIdentity, IdentityInfo>()

    fun setIdentityInfo(identity: HoldingIdentity, identityInfo: IdentityInfo) {
        identityMap[identity] = identityInfo
    }

    override fun isHostedLocally(identity: HoldingIdentity): Boolean {
        return identity in identityMap.keys
    }

    override fun pollForIdentityInfo(identity: HoldingIdentity): IdentityInfo? {
        return identityMap[identity]
    }

    override fun getAllVersionedRecords(): Stream<VersionedRecord<String, HostedIdentityEntry>> =
        emptyList<VersionedRecord<String, HostedIdentityEntry>>().stream()

    override val lifecycleCoordinatorName: LifecycleCoordinatorName
        get() = coordinator.name

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }
}