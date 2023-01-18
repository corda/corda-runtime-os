package net.corda.processors.member.internal

import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.client.hsm.HSMRegistrationClient
import net.corda.crypto.hes.StableKeyPairDecryptor
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.membership.groupparams.writer.service.GroupParametersWriterService
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.locally.hosted.identities.LocallyHostedIdentitiesService
import net.corda.membership.p2p.MembershipP2PReadService
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.read.GroupParametersReaderService
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.membership.registration.RegistrationManagementService
import net.corda.membership.registration.RegistrationProxy
import net.corda.membership.service.MemberOpsService
import net.corda.membership.synchronisation.SynchronisationProxy
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
    private val memberOpsService: MemberOpsService,
    @Reference(service = HSMRegistrationClient::class)
    private val hsmRegistrationClient: HSMRegistrationClient,
    @Reference(service = MembershipP2PReadService::class)
    private val membershipP2PReadService: MembershipP2PReadService,
    @Reference(service = MembershipPersistenceClient::class)
    private val membershipPersistenceClient: MembershipPersistenceClient,
    @Reference(service = MembershipQueryClient::class)
    private val membershipQueryClient: MembershipQueryClient,
    @Reference(service = RegistrationManagementService::class)
    private val registrationManagementService: RegistrationManagementService,
    @Reference(service = MembershipGroupReaderProvider::class)
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    @Reference(service = SynchronisationProxy::class)
    private val synchronisationProxy: SynchronisationProxy,
    @Reference(service = StableKeyPairDecryptor::class)
    private val stableKeyPairDecryptor: StableKeyPairDecryptor,
    @Reference(service = GroupParametersWriterService::class)
    private val groupParametersWriterService: GroupParametersWriterService,
    @Reference(service = GroupParametersReaderService::class)
    private val groupParametersReaderService: GroupParametersReaderService,
    @Reference(service = LocallyHostedIdentitiesService::class)
    private val locallyHostedIdentitiesService: LocallyHostedIdentitiesService,
) : MemberProcessor {

    private companion object {
        private val logger = contextLogger()
    }

    private val dependentComponents = DependentComponents.of(
        ::configurationReadService,
        ::virtualNodeInfoReadService,
        ::cpiInfoReader,
        ::groupPolicyProvider,
        ::hsmRegistrationClient,
        ::registrationProxy,
        ::cryptoOpsClient,
        ::memberOpsService,
        ::membershipP2PReadService,
        ::membershipPersistenceClient,
        ::membershipQueryClient,
        ::registrationManagementService,
        ::membershipGroupReaderProvider,
        ::synchronisationProxy,
        ::stableKeyPairDecryptor,
        ::groupParametersWriterService,
        ::groupParametersReaderService,
        ::locallyHostedIdentitiesService,
    )

    private val coordinator =
        lifecycleCoordinatorFactory.createCoordinator<MemberProcessor>(
            dependentComponents,
            MemberProcessorLifecycleHandler(configurationReadService)
        )


    override fun start(bootConfig: SmartConfig) {
        logger.info("Member processor starting.")
        coordinator.start()
        coordinator.postEvent(BootConfigEvent(bootConfig))
    }

    override fun stop() = coordinator.stop()
}
