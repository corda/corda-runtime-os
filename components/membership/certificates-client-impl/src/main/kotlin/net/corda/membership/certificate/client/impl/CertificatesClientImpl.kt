package net.corda.membership.certificate.client.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.data.certificates.CertificateUsage
import net.corda.data.certificates.rpc.request.CertificateRpcRequest
import net.corda.data.certificates.rpc.request.ImportCertificateRpcRequest
import net.corda.data.certificates.rpc.request.ListCertificateAliasesRpcRequest
import net.corda.data.certificates.rpc.request.RetrieveCertificateRpcRequest
import net.corda.data.certificates.rpc.response.CertificateImportedRpcResponse
import net.corda.data.certificates.rpc.response.CertificateRetrievalRpcResponse
import net.corda.data.certificates.rpc.response.CertificateRpcResponse
import net.corda.data.certificates.rpc.response.ListCertificateAliasRpcResponse
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.membership.certificate.client.CertificatesClient
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import net.corda.utilities.concurrent.getOrThrow
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("LongParameterList")
@Component(service = [CertificatesClient::class])
class CertificatesClientImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = VirtualNodeInfoReadService::class)
    virtualNodeInfoReadService: VirtualNodeInfoReadService,
    @Reference(service = CryptoOpsClient::class)
    cryptoOpsClient: CryptoOpsClient,
    @Reference(service = KeyEncodingService::class)
    keyEncodingService: KeyEncodingService,
    @Reference(service = GroupPolicyProvider::class)
    groupPolicyProvider: GroupPolicyProvider,
    @Reference(service = MembershipGroupReaderProvider::class)
    membershipGroupReaderProvider: MembershipGroupReaderProvider,
    @Reference(service = MembershipPersistenceClient::class)
    membershipPersistenceClient : MembershipPersistenceClient,
    @Reference(service = MembershipQueryClient::class)
    membershipQueryClient : MembershipQueryClient,
    @Reference(service = LayeredPropertyMapFactory::class)
    layeredPropertyMapFactory: LayeredPropertyMapFactory,
) : CertificatesClient {
    private companion object {
        const val GROUP_NAME = "membership.db.certificates.client.group"
        const val CLIENT_NAME = "membership.db.certificates.client"
        const val PUBLISHER_NAME = "membership.certificates.publisher"
    }

    private var sender: RPCSender<CertificateRpcRequest, CertificateRpcResponse>? = null
    private val coordinator = coordinatorFactory.createCoordinator<CertificatesClient>(::handleEvent)
    private var registrationHandle: AutoCloseable? = null
    private var senderRegistrationHandle: AutoCloseable? = null
    private var configHandle: AutoCloseable? = null
    private var publisher: Publisher? = null

    private val mtlsMgmClientCertificateKeeper = MtlsMgmClientCertificateKeeper(
        membershipGroupReaderProvider,
        membershipPersistenceClient,
        membershipQueryClient,
        layeredPropertyMapFactory,
    )
    private val hostedIdentityEntryFactory = HostedIdentityEntryFactory(
        virtualNodeInfoReadService = virtualNodeInfoReadService,
        cryptoOpsClient = cryptoOpsClient,
        keyEncodingService = keyEncodingService,
        groupPolicyProvider = groupPolicyProvider,
        mtlsMgmClientCertificateKeeper = mtlsMgmClientCertificateKeeper,
        retrieveCertificates = ::retrieveCertificates,
    )

    override fun importCertificates(
        usage: CertificateUsage,
        holdingIdentityId: ShortHash?,
        alias: String,
        certificates: String,
    ) {
        send<CertificateImportedRpcResponse>(holdingIdentityId, usage, ImportCertificateRpcRequest(alias, certificates))
    }

    override fun getCertificateAliases(usage: CertificateUsage, holdingIdentityId: ShortHash?): Collection<String> {
        return send<ListCertificateAliasRpcResponse>(holdingIdentityId, usage, ListCertificateAliasesRpcRequest())?.aliases ?: emptyList()
    }

    override fun retrieveCertificates(holdingIdentityId: ShortHash?, usage: CertificateUsage, alias: String): String? {
        return send<CertificateRetrievalRpcResponse>(holdingIdentityId, usage, RetrieveCertificateRpcRequest(alias))?.certificates
    }

    override fun setupLocallyHostedIdentity(
        holdingIdentityShortHash: ShortHash,
        p2pTlsCertificateChainAlias: String,
        useClusterLevelTlsCertificateAndKey: Boolean,
        sessionKeyId: String?,
        sessionCertificateChainAlias: String?
    ) {
        val record = hostedIdentityEntryFactory.createIdentityRecord(
            holdingIdentityShortHash = holdingIdentityShortHash,
            tlsCertificateChainAlias = p2pTlsCertificateChainAlias,
            useClusterLevelTlsCertificateAndKey = useClusterLevelTlsCertificateAndKey,
            sessionCertificateChainAlias = sessionCertificateChainAlias,
            sessionKeyId = sessionKeyId,
        )

        val futures = publisher?.publish(
            listOf(
                record,
            )
        ) ?: throw IllegalStateException("publisher is not ready")

        futures.forEach {
            it.getOrThrow()
        }
    }

    override val isRunning: Boolean
        get() = sender != null

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }
    private inline fun <reified R> send(
        holdingIdentityId: ShortHash?,
        usage: CertificateUsage,
        payload: Any,
    ): R? {
        val currentSender = sender
        return if (currentSender == null) {
            throw IllegalStateException("Certificates client is not ready")
        } else {
            currentSender.sendRequest(
                CertificateRpcRequest(
                    usage,
                    holdingIdentityId?.value,
                    payload,
                )
            ).getOrThrow()?.response as? R
        }
    }

    private fun handleStartEvent() {
        registrationHandle?.close()
        registrationHandle = coordinator.followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>(),
                LifecycleCoordinatorName.forComponent<CryptoOpsClient>(),
                LifecycleCoordinatorName.forComponent<GroupPolicyProvider>(),
                LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>(),
                LifecycleCoordinatorName.forComponent<MembershipPersistenceClient>(),
                LifecycleCoordinatorName.forComponent<MembershipQueryClient>(),
            )
        )
    }

    private fun handleStopEvent() {
        coordinator.updateStatus(
            LifecycleStatus.DOWN,
            "Component received stop event."
        )
        senderRegistrationHandle?.close()
        senderRegistrationHandle = null
        registrationHandle?.close()
        registrationHandle = null
        configHandle?.close()
        configHandle = null
        sender?.close()
        sender = null
        publisher?.close()
        publisher = null
    }

    private fun handleRegistrationStatusChangeEvent(event: RegistrationStatusChangeEvent, coordinator: LifecycleCoordinator) {
        if (event.status == LifecycleStatus.UP) {
            if (event.registration == registrationHandle) {
                configHandle?.close()
                configHandle = configurationReadService.registerComponentForUpdates(
                    coordinator,
                    setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG)
                )
            } else if (event.registration == senderRegistrationHandle) {
                coordinator.updateStatus(
                    LifecycleStatus.UP,
                    "Received config and started RPC topic subscription."
                )
            }
        } else {
            if (event.registration == registrationHandle) {
                configHandle?.close()
                coordinator.updateStatus(
                    event.status,
                    "Configuration read service went down"
                )
            } else if (event.registration == senderRegistrationHandle) {
                coordinator.updateStatus(
                    event.status,
                    "Sender went down"
                )
            }
        }
    }

    private fun handleConfigChangedEvent(event: ConfigChangedEvent) {
        senderRegistrationHandle?.close()
        sender?.close()
        senderRegistrationHandle = null
        sender = publisherFactory.createRPCSender(
            rpcConfig = RPCConfig(
                groupName = GROUP_NAME,
                clientName = CLIENT_NAME,
                requestTopic = Schemas.Certificates.CERTIFICATES_RPC_TOPIC,
                requestType = CertificateRpcRequest::class.java,
                responseType = CertificateRpcResponse::class.java,
            ),
            messagingConfig = event.config.getConfig(ConfigKeys.MESSAGING_CONFIG),
        ).also {
            senderRegistrationHandle = coordinator.followStatusChangesByName(
                setOf(
                    it.subscriptionName
                )
            )
            it.start()
        }
        publisher?.close()
        publisher = publisherFactory.createPublisher(
            publisherConfig = PublisherConfig(
                clientId = PUBLISHER_NAME,
                transactional = false,
            ),
            messagingConfig = event.config.getConfig(ConfigKeys.MESSAGING_CONFIG),
        ).also {
            it.start()
        }
    }

    private fun handleEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                handleStartEvent()
            }
            is StopEvent -> {
                handleStopEvent()
            }
            is RegistrationStatusChangeEvent -> {
                handleRegistrationStatusChangeEvent(event, coordinator)
            }
            is ConfigChangedEvent -> {
                handleConfigChangedEvent(event)
            }
        }
    }
}
