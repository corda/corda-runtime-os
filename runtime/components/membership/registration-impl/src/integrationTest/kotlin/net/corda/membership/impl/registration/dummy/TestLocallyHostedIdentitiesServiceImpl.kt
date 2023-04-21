package net.corda.membership.impl.registration.dummy

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

@Component(service = [LocallyHostedIdentitiesService::class])
internal class TestLocallyHostedIdentitiesServiceImpl  @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
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
    override fun getIdentityInfo(identity: HoldingIdentity): IdentityInfo? {
        throw UnsupportedOperationException()
    }

    override val isRunning = true

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }
}
