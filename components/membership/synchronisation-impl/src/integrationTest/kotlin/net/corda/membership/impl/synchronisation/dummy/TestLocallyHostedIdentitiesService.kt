package net.corda.membership.impl.synchronisation.dummy

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.membership.locally.hosted.identities.IdentityInfo
import net.corda.membership.locally.hosted.identities.LocallyHostedIdentitiesService
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.propertytypes.ServiceRanking
import java.util.concurrent.ConcurrentHashMap

interface TestLocallyHostedIdentitiesService : LocallyHostedIdentitiesService {
    fun setIdentityInfo(info: IdentityInfo)
}

@ServiceRanking(Int.MAX_VALUE)
@Component(service = [LocallyHostedIdentitiesService::class, TestLocallyHostedIdentitiesService::class])
class TestLocallyHostedIdentitiesServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
) : TestLocallyHostedIdentitiesService {
    private val coordinator = coordinatorFactory.createCoordinator(
        LifecycleCoordinatorName.forComponent<LocallyHostedIdentitiesService>(),
    ) { event, coordinator ->
        if (event is StartEvent) {
            coordinator.updateStatus(LifecycleStatus.UP)
        }
    }
    private val identities = ConcurrentHashMap<HoldingIdentity, IdentityInfo>()

    override fun setIdentityInfo(info: IdentityInfo) {
        identities[info.identity] = info
    }

    override fun isHostedLocally(identity: HoldingIdentity): Boolean {
        return identity in identities.keys
    }

    override fun pollForIdentityInfo(identity: HoldingIdentity): IdentityInfo? {
        return identities[identity]
    }

    override val isRunning: Boolean
        get() = coordinator.status == LifecycleStatus.UP

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }
}
