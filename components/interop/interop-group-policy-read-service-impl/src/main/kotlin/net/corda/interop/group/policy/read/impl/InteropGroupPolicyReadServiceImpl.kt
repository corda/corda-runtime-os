package net.corda.interop.group.policy.read.impl

import net.corda.configuration.read.ConfigurationReadService
import net.corda.interop.group.policy.read.InteropGroupPolicyReadService
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory


@Component(service = [InteropGroupPolicyReadService::class])
class InteropGroupPolicyReadServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory
) : InteropGroupPolicyReadService {
    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val lifecycleEventHandler = InteropGroupPolicyReadServiceEventHandler(
        configurationReadService, subscriptionFactory
    )

    private val dependentComponents = DependentComponents.of(
        ::configurationReadService
    )

    private val coordinatorName = LifecycleCoordinatorName.forComponent<InteropGroupPolicyReadService>()
    private val coordinator = coordinatorFactory.createCoordinator(coordinatorName, dependentComponents, lifecycleEventHandler)

    override fun getGroupPolicy(groupId: String) : String? {
        return lifecycleEventHandler.getGroupPolicy(groupId)
    }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        // Use debug rather than info
        log.info("Component starting")
        coordinator.start()
    }

    override fun stop() {
        //  Use debug rather than info
        log.info("Component stopping")
        coordinator.stop()
    }
}