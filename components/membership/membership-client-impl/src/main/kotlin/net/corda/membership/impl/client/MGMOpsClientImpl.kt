package net.corda.membership.impl.client

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.membership.preauth.PreAuthToken
import net.corda.data.membership.preauth.PreAuthTokenStatus
import net.corda.data.membership.rpc.request.MGMGroupPolicyRequest
import net.corda.data.membership.rpc.request.MembershipRpcRequest
import net.corda.data.membership.rpc.request.MembershipRpcRequestContext
import net.corda.data.membership.rpc.response.MGMGroupPolicyResponse
import net.corda.data.membership.rpc.response.MembershipRpcResponse
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
import net.corda.membership.client.CouldNotFindMemberException
import net.corda.membership.client.MGMOpsClient
import net.corda.membership.client.MemberNotAnMgmException
import net.corda.membership.client.dto.PreAuthTokenDto
import net.corda.membership.client.dto.PreAuthTokenStatusDTO
import net.corda.membership.lib.MemberInfoExtension.Companion.isMgm
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import net.corda.utilities.concurrent.getOrThrow
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.seconds
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import java.time.Instant
import java.util.UUID

@Component(service = [MGMOpsClient::class])
@Suppress("LongParameterList")
class MGMOpsClientImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PublisherFactory::class)
    val publisherFactory: PublisherFactory,
    @Reference(service = ConfigurationReadService::class)
    val configurationReadService: ConfigurationReadService,
    @Reference(service = MembershipGroupReaderProvider::class)
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    @Reference(service = MembershipPersistenceClient::class)
    private val membershipPersistenceClient: MembershipPersistenceClient,
    @Reference(service = MembershipQueryClient::class)
    private val membershipQueryClient: MembershipQueryClient,
) : MGMOpsClient {

    companion object {
        private val logger: Logger = contextLogger()
        const val ERROR_MSG = "Service is in an incorrect state for calling."

        const val CLIENT_ID = "mgm-ops-client"
        const val GROUP_NAME = "mgm-ops-client"

        private val clock = UTCClock()

        private val TIMEOUT = 10.seconds
    }

    private interface InnerMGMOpsClient : AutoCloseable {
        fun generateGroupPolicy(holdingIdentityShortHash: ShortHash): String
        fun mutualTlsAllowClientCertificate(
            holdingIdentityShortHash: ShortHash,
            subject: MemberX500Name,
        )
        fun mutualTlsDisallowClientCertificate(
            holdingIdentityShortHash: ShortHash,
            subject: MemberX500Name,
        )
        fun mutualTlsListClientCertificate(
            holdingIdentityShortHash: ShortHash,
        ): Collection<MemberX500Name>

        fun generatePreAuthToken(
            holdingIdentityShortHash: ShortHash,
            ownerX500Name: MemberX500Name,
            ttl: Int,
            remarks: String?
        ): PreAuthTokenDto

        fun getPreAuthTokens(
            holdingIdentityShortHash: ShortHash,
            ownerX500Name: MemberX500Name?,
            preAuthTokenId: UUID?,
            viewInactive: Boolean
        ): Collection<PreAuthTokenDto>

        fun revokePreAuthToken(holdingIdentityShortHash: ShortHash, preAuthTokenId: UUID, remarks: String? = null): PreAuthTokenDto
    }

    private var impl: InnerMGMOpsClient = InactiveImpl

    // for watching the config changes
    private var configHandle: AutoCloseable? = null

    // for checking the components' health
    private var componentHandle: AutoCloseable? = null

    private val coordinator = coordinatorFactory.createCoordinator<MGMOpsClient>(::processEvent)

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }

    override fun generateGroupPolicy(holdingIdentityShortHash: ShortHash) =
        impl.generateGroupPolicy(holdingIdentityShortHash)

    override fun mutualTlsAllowClientCertificate(
        holdingIdentityShortHash: ShortHash,
        subject: MemberX500Name,
    ) = impl.mutualTlsAllowClientCertificate(
        holdingIdentityShortHash,
        subject,
    )

    override fun mutualTlsDisallowClientCertificate(
        holdingIdentityShortHash: ShortHash,
        subject: MemberX500Name,
    ) = impl.mutualTlsDisallowClientCertificate(
        holdingIdentityShortHash,
        subject,
    )

    override fun mutualTlsListClientCertificate(
        holdingIdentityShortHash: ShortHash
    ) = impl.mutualTlsListClientCertificate(
        holdingIdentityShortHash,
    )

    override fun generatePreAuthToken(
        holdingIdentityShortHash: ShortHash,
        ownerX500Name: MemberX500Name,
        ttl: Int,
        remarks: String?
    ) = impl.generatePreAuthToken(holdingIdentityShortHash, ownerX500Name, ttl, remarks)

    override fun getPreAuthTokens(
        holdingIdentityShortHash: ShortHash,
        ownerX500Name: MemberX500Name?,
        preAuthTokenId: UUID?,
        viewInactive: Boolean
    ) = impl.getPreAuthTokens(holdingIdentityShortHash, ownerX500Name, preAuthTokenId, viewInactive)

    override fun revokePreAuthToken(holdingIdentityShortHash: ShortHash, preAuthTokenId: UUID, remarks: String?) =
        impl.revokePreAuthToken(holdingIdentityShortHash, preAuthTokenId, remarks)

    private fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                componentHandle?.close()
                componentHandle = coordinator.followStatusChangesByName(
                    setOf(
                        LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                        LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>(),
                        LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>(),
                        LifecycleCoordinatorName.forComponent<MembershipPersistenceClient>(),
                        LifecycleCoordinatorName.forComponent<MembershipQueryClient>(),
                    )
                )
            }
            is StopEvent -> {
                componentHandle?.close()
                configHandle?.close()
                deactivate("Handling the stop event for component.")
            }
            is RegistrationStatusChangeEvent -> {
                when (event.status) {
                    LifecycleStatus.UP -> {
                        configHandle?.close()
                        configHandle = configurationReadService.registerComponentForUpdates(
                            coordinator,
                            setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG)
                        )
                    }
                    else -> {
                        configHandle?.close()
                        deactivate("Service dependencies have changed status causing this component to deactivate.")
                    }
                }
            }
            is ConfigChangedEvent -> {
                impl.close()
                impl = ActiveImpl(
                    publisherFactory.createRPCSender(
                        RPCConfig(
                            groupName = GROUP_NAME,
                            clientName = CLIENT_ID,
                            requestTopic = Schemas.Membership.MEMBERSHIP_RPC_TOPIC,
                            requestType = MembershipRpcRequest::class.java,
                            responseType = MembershipRpcResponse::class.java
                        ),
                        event.config.getConfig(ConfigKeys.MESSAGING_CONFIG)
                    ).also {
                        it.start()
                    }
                )
                coordinator.updateStatus(LifecycleStatus.UP, "Dependencies are UP and configuration received.")
            }
        }
    }

    private fun deactivate(reason: String) {
        coordinator.updateStatus(LifecycleStatus.DOWN, reason)
        val current = impl
        impl = InactiveImpl
        current.close()
    }

    private object InactiveImpl : InnerMGMOpsClient {
        override fun generateGroupPolicy(holdingIdentityShortHash: ShortHash) =
            throw IllegalStateException(ERROR_MSG)

        override fun mutualTlsAllowClientCertificate(
            holdingIdentityShortHash: ShortHash,
            subject: MemberX500Name,
        ) = throw IllegalStateException(ERROR_MSG)

        override fun mutualTlsDisallowClientCertificate(
            holdingIdentityShortHash: ShortHash,
            subject: MemberX500Name,
        ) = throw IllegalStateException(ERROR_MSG)

        override fun mutualTlsListClientCertificate(
            holdingIdentityShortHash: ShortHash,
        ) = throw IllegalStateException(ERROR_MSG)

        override fun generatePreAuthToken(
            holdingIdentityShortHash: ShortHash,
            ownerX500Name: MemberX500Name,
            ttl: Int,
            remarks: String?
        ) = throw IllegalStateException(ERROR_MSG)

        override fun getPreAuthTokens(
            holdingIdentityShortHash: ShortHash,
            ownerX500Name: MemberX500Name?,
            preAuthTokenId: UUID?,
            viewInactive: Boolean
        ) = throw IllegalStateException(ERROR_MSG)

        override fun revokePreAuthToken(holdingIdentityShortHash: ShortHash, preAuthTokenId: UUID, remarks: String?) =
            throw IllegalStateException(ERROR_MSG)

        override fun close() = Unit
    }

    private inner class ActiveImpl(
        val rpcSender: RPCSender<MembershipRpcRequest, MembershipRpcResponse>
    ) : InnerMGMOpsClient {

        @Suppress("ThrowsCount")
        fun mgmHoldingIdentity(holdingIdentityShortHash: ShortHash): HoldingIdentity {
            val holdingIdentity =
                virtualNodeInfoReadService.getByHoldingIdentityShortHash(holdingIdentityShortHash)?.holdingIdentity
                    ?: throw CouldNotFindMemberException(holdingIdentityShortHash)

            val reader = membershipGroupReaderProvider.getGroupReader(holdingIdentity)

            val filteredMembers =
                reader.lookup(holdingIdentity.x500Name)
                    ?:throw CouldNotFindMemberException(holdingIdentityShortHash)

            if (!filteredMembers.isMgm) {
                throw MemberNotAnMgmException(holdingIdentityShortHash)
            }
            return holdingIdentity
        }
        override fun generateGroupPolicy(holdingIdentityShortHash: ShortHash): String {
            mgmHoldingIdentity(holdingIdentityShortHash)

            val request = MembershipRpcRequest(
                MembershipRpcRequestContext(
                    UUID.randomUUID().toString(),
                    clock.instant()
                ),
                MGMGroupPolicyRequest(holdingIdentityShortHash.toString())
            )

            return generateGroupPolicyResponse(request.sendRequest())

        }

        override fun mutualTlsAllowClientCertificate(holdingIdentityShortHash: ShortHash, subject: MemberX500Name) {
            val mgmHoldingIdentity = mgmHoldingIdentity(holdingIdentityShortHash)

            membershipPersistenceClient.mutualTlsAddCertificateToAllowedList(
                mgmHoldingIdentity,
                subject.toString(),
            ).getOrThrow()
        }

        override fun mutualTlsDisallowClientCertificate(holdingIdentityShortHash: ShortHash, subject: MemberX500Name) {
            val mgmHoldingIdentity = mgmHoldingIdentity(holdingIdentityShortHash)

            membershipPersistenceClient.mutualTlsRemoveCertificateFromAllowedList(
                mgmHoldingIdentity,
                subject.toString(),
            ).getOrThrow()
        }

        override fun mutualTlsListClientCertificate(holdingIdentityShortHash: ShortHash): Collection<MemberX500Name> {
            val mgmHoldingIdentity = mgmHoldingIdentity(holdingIdentityShortHash)
            return membershipQueryClient.mutualTlsListAllowedCertificates(
                mgmHoldingIdentity,
            ).getOrThrow()
                .map {
                    MemberX500Name.parse(it)
                }
        }

        override fun generatePreAuthToken(
            holdingIdentityShortHash: ShortHash,
            ownerX500Name: MemberX500Name,
            ttl: Int,
            remarks: String?
        ): PreAuthTokenDto {
            val mgmHoldingIdentity = mgmHoldingIdentity(holdingIdentityShortHash)
            val tokenId = UUID.randomUUID()
            membershipPersistenceClient.generatePreAuthToken(mgmHoldingIdentity, UUID.randomUUID(), ownerX500Name, ttl, remarks)
                .getOrThrow()
            return PreAuthTokenDto(
                tokenId.toString(),
                ownerX500Name.toString(),
                Instant.ofEpochMilli(ttl.toLong()),
                PreAuthTokenStatusDTO.AVAILABLE,
                remarks
            )
        }

        override fun getPreAuthTokens(
            holdingIdentityShortHash: ShortHash,
            ownerX500Name: MemberX500Name?,
            preAuthTokenId: UUID?,
            viewInactive: Boolean
        ): Collection<PreAuthTokenDto> {
            val mgmHoldingIdentity = mgmHoldingIdentity(holdingIdentityShortHash)
            return membershipQueryClient.queryPreAuthTokens(
                mgmHoldingIdentity,
                ownerX500Name,
                preAuthTokenId,
                viewInactive
            ).getOrThrow().map { it.toDto() }
        }

        override fun revokePreAuthToken(holdingIdentityShortHash: ShortHash, preAuthTokenId: UUID, remarks: String?): PreAuthTokenDto {
            val mgmHoldingIdentity = mgmHoldingIdentity(holdingIdentityShortHash)
            return membershipPersistenceClient.revokePreAuthToken(mgmHoldingIdentity, preAuthTokenId, remarks).getOrThrow().toDto()
        }

        override fun close() = rpcSender.close()

        private fun PreAuthToken.toDto(): PreAuthTokenDto =
            PreAuthTokenDto(this.id, this.ownerX500Name, Instant.ofEpochMilli(this.ttl), this.status.toDto(), this.remark)

        private fun PreAuthTokenStatus.toDto(): PreAuthTokenStatusDTO = when(this) {
            PreAuthTokenStatus.AVAILABLE -> PreAuthTokenStatusDTO.AVAILABLE
            PreAuthTokenStatus.REVOKED -> PreAuthTokenStatusDTO.REVOKED
            PreAuthTokenStatus.CONSUMED -> PreAuthTokenStatusDTO.CONSUMED
            PreAuthTokenStatus.AUTO_INVALIDATED -> PreAuthTokenStatusDTO.AUTO_INVALIDATED
        }

        @Suppress("SpreadOperator")
        private fun generateGroupPolicyResponse(response: MGMGroupPolicyResponse): String =
            response.groupPolicy.toString()

        private inline fun <reified RESPONSE> MembershipRpcRequest.sendRequest(): RESPONSE {
            try {
                logger.debug { "Sending request: $this" }
                val response = rpcSender.sendRequest(this).getOrThrow(TIMEOUT)
                require(response != null && response.responseContext != null && response.response != null) {
                    "Response cannot be null."
                }
                require(this.requestContext.requestId == response.responseContext.requestId) {
                    "Request ID must match in the request and response."
                }
                require(this.requestContext.requestTimestamp == response.responseContext.requestTimestamp) {
                    "Request timestamp must match in the request and response."
                }
                require(response.response is RESPONSE) {
                    "Expected ${RESPONSE::class.java} as response type, but received ${response.response.javaClass}."
                }

                return response.response as RESPONSE
            } catch (e: Exception) {
                throw CordaRuntimeException(
                    "Failed to send request and receive response for MGM RPC operation. " + e.message, e
                )
            }
        }

    }
}
