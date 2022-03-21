package net.corda.processors.member.internal

import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.service.MemberOpsService
import net.corda.membership.registration.proxy.RegistrationProxy
import net.corda.processors.member.MemberProcessor
import net.corda.processors.member.internal.lifecycle.MemberProcessorLifecycleHandler
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("LongParameterList")
@Component(service = [MemberProcessor::class])
class MemberProcessorImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = RegistrationProxy::class)
    private val registrationProxy: RegistrationProxy,
    @Reference(service = GroupPolicyProvider::class)
    private val groupPolicyProvider: GroupPolicyProvider,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    @Reference(service = CpiInfoReadService::class)
    private val cpiInfoReader: CpiInfoReadService,
    @Reference(service = CryptoOpsClient::class)
    private val cryptoOpsClient: CryptoOpsClient,
    @Reference(service = MemberOpsService::class)
    private val memberOpsService: MemberOpsService
) : MemberProcessor {

    companion object {
        val logger = contextLogger()
    }

    private val dependentComponents = DependentComponents.of(
        ::configurationReadService,
        ::virtualNodeInfoReadService,
        ::cpiInfoReader,
        ::groupPolicyProvider,
        ::registrationProxy,
        ::cryptoOpsClient,
        ::memberOpsService
    )

    private val coordinator =
        lifecycleCoordinatorFactory.createCoordinator<MemberProcessor>(
            MemberProcessorLifecycleHandler(configurationReadService, dependentComponents)
        )


    override fun start(bootConfig: SmartConfig) {
        logger.info("Member processor starting.")
        coordinator.start()
        coordinator.postEvent(BootConfigEvent(bootConfig))
    }

    override fun stop() = coordinator.stop()
}