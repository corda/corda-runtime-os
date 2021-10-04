package net.corda.membership.impl

import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.membership.MembershipGroupFactory
import net.corda.membership.MembershipGroupInfoLookupServiceProvider
import net.corda.membership.impl.lifecycle.AbstractMembershipCoordinator
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component
class MembershipCoordinator @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = MembershipGroupFactory::class)
    private val factory: MembershipGroupFactory,
    @Reference(service = MembershipGroupInfoLookupServiceProvider::class)
    private val provider: MembershipGroupInfoLookupServiceProvider
) : AbstractMembershipCoordinator(
    LifecycleCoordinatorName.forComponent<MembershipCoordinator>(),
    coordinatorFactory,
    configurationReadService,
    listOf(
        factory,
        provider
    )
)
