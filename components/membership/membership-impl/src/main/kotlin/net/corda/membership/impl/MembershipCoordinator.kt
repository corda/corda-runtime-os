package net.corda.membership.impl

import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.membership.impl.lifecycle.AbstractMembershipCoordinator
import net.corda.membership.read.MembershipGroupReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component
class MembershipCoordinator @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = MembershipGroupReadService::class)
    private val membershipGroupReadService: MembershipGroupReadService
) : AbstractMembershipCoordinator(
    LifecycleCoordinatorName.forComponent<MembershipCoordinator>(),
    coordinatorFactory,
    configurationReadService,
    listOf(
        membershipGroupReadService
    )
)
