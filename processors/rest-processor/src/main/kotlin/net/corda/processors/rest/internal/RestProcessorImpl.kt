package net.corda.processors.rest.internal

import net.corda.components.rpc.RestGateway
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpi.upload.endpoints.service.CpiUploadRPCOpsService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.client.hsm.HSMRegistrationClient
import net.corda.flow.rpcops.FlowRPCOpsService
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.merger.ConfigMerger
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.membership.certificate.client.CertificatesClient
import net.corda.membership.client.MGMResourceClient
import net.corda.membership.client.MemberResourceClient
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.read.GroupParametersReaderService
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.processors.rest.RestProcessor
import net.corda.v5.base.util.debug
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

/** The processor for a `RestWorker`. */
@Component(service = [RestProcessor::class])
@Suppress("Unused", "LongParameterList")
class RestProcessorImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configReadService: ConfigurationReadService,
    @Reference(service = RestGateway::class)
    private val restGateway: RestGateway,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = FlowRPCOpsService::class)
    private val flowRPCOpsService: FlowRPCOpsService,
    @Reference(service = CpiUploadRPCOpsService::class)
    private val cpiUploadRPCOpsService: CpiUploadRPCOpsService,
    @Reference(service = CpiInfoReadService::class)
    private val cpiInfoReadService: CpiInfoReadService,
    @Reference(service = MemberResourceClient::class)
    private val memberResourceClient: MemberResourceClient,
    @Reference(service = MGMResourceClient::class)
    private val mgmResourceClient: MGMResourceClient,
    @Reference(service = MembershipGroupReaderProvider::class)
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    @Reference(service = ConfigMerger::class)
    private val configMerger: ConfigMerger,
    @Reference(service = CryptoOpsClient::class)
    private val cryptoOpsClient: CryptoOpsClient,
    @Reference(service = HSMRegistrationClient::class)
    private val hsmRegistrationClient: HSMRegistrationClient,
    @Reference(service = CertificatesClient::class)
    private val certificatesClient: CertificatesClient,
    @Reference(service = GroupPolicyProvider::class)
    private val groupPolicyProvider: GroupPolicyProvider,
    @Reference(service = MembershipQueryClient::class)
    private val membershipQueryClient: MembershipQueryClient,
    @Reference(service = MembershipPersistenceClient::class)
    private val membershipPersistenceClient: MembershipPersistenceClient,
    @Reference(service = GroupParametersReaderService::class)
    private val groupParametersReaderService: GroupParametersReaderService,
) : RestProcessor {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)

        const val CLIENT_ID_REST_PROCESSOR = "rest.processor"
    }

    private val dependentComponents = DependentComponents.of(
        ::configReadService,
        ::restGateway,
        ::flowRPCOpsService,
        ::cpiUploadRPCOpsService,
        ::cpiInfoReadService,
        ::memberResourceClient,
        ::mgmResourceClient,
        ::membershipGroupReaderProvider,
        ::virtualNodeInfoReadService,
        ::cryptoOpsClient,
        ::hsmRegistrationClient,
        ::certificatesClient,
        ::groupPolicyProvider,
        ::membershipQueryClient,
        ::membershipPersistenceClient,
        ::groupParametersReaderService,
    )
    private val lifecycleCoordinator = coordinatorFactory.createCoordinator<RestProcessorImpl>(dependentComponents, ::eventHandler)

    override fun start(bootConfig: SmartConfig) {
        log.info("REST processor starting.")
        lifecycleCoordinator.start()
        lifecycleCoordinator.postEvent(BootConfigEvent(bootConfig))
    }

    override fun stop() {
        log.info("REST processor stopping.")
        lifecycleCoordinator.stop()
    }

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        log.debug { "REST processor received event $event." }
        when (event) {
            is StartEvent -> {
                // Nothing to do
            }
            is RegistrationStatusChangeEvent -> {
                log.info("REST processor is ${event.status}")
                coordinator.updateStatus(event.status)
            }
            is BootConfigEvent -> {
                configReadService.bootstrapConfig(event.config)
            }
            is StopEvent -> {
                // Nothing to do
            }
            else -> {
                log.error("Unexpected event $event!")
            }
        }
    }
}

data class BootConfigEvent(val config: SmartConfig) : LifecycleEvent
