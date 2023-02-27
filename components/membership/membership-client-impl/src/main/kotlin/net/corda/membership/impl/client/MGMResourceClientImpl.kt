package net.corda.membership.impl.client

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.ApproveRegistration
import net.corda.data.membership.command.registration.mgm.DeclineRegistration
import net.corda.data.membership.common.ApprovalRuleDetails
import net.corda.data.membership.common.ApprovalRuleType
import net.corda.data.membership.common.RegistrationStatus
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
import net.corda.membership.client.MGMResourceClient
import net.corda.membership.client.MemberNotAnMgmException
import net.corda.membership.lib.MemberInfoExtension.Companion.isMgm
import net.corda.membership.lib.approval.ApprovalRuleParams
import net.corda.membership.lib.registration.RegistrationRequestStatus
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.schema.Schemas
import net.corda.schema.Schemas.Membership.REGISTRATION_COMMAND_TOPIC
import net.corda.schema.configuration.ConfigKeys
import net.corda.utilities.concurrent.getOrThrow
import net.corda.utilities.debug
import net.corda.utilities.time.UTCClock
import net.corda.utilities.seconds
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

@Component(service = [MGMResourceClient::class])
@Suppress("LongParameterList", "TooManyFunctions")
class MGMResourceClientImpl @Activate constructor(
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
) : MGMResourceClient {

    private companion object {
        const val ERROR_MSG = "Service is in an incorrect state for calling."
        const val CLIENT_ID = "mgm-resource-client"
        const val GROUP_NAME = "mgm-resource-client"
        const val FOLLOW_CHANGES_RESOURCE_NAME = "MGMResourceClient.followStatusChangesByName"
        const val WAIT_FOR_CONFIG_RESOURCE_NAME = "MGMResourceClient.registerComponentForUpdates"
        const val PUBLISHER_RESOURCE_NAME = "MGMResourceClient.publisher"

        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        val clock = UTCClock()
        val TIMEOUT = 10.seconds
    }

    private interface InnerMGMResourceClient : AutoCloseable {
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
            ttl: Instant?,
            remarks: String?
        ): PreAuthToken

        fun getPreAuthTokens(
            holdingIdentityShortHash: ShortHash,
            ownerX500Name: MemberX500Name?,
            preAuthTokenId: UUID?,
            viewInactive: Boolean
        ): Collection<PreAuthToken>

        fun revokePreAuthToken(holdingIdentityShortHash: ShortHash, preAuthTokenId: UUID, remarks: String? = null): PreAuthToken
        fun addApprovalRule(
            holdingIdentityShortHash: ShortHash,
            ruleParams: ApprovalRuleParams
        ): ApprovalRuleDetails

        fun getApprovalRules(holdingIdentityShortHash: ShortHash, ruleType: ApprovalRuleType):
                Collection<ApprovalRuleDetails>

        fun deleteApprovalRule(holdingIdentityShortHash: ShortHash, ruleId: String, ruleType: ApprovalRuleType)

        fun viewRegistrationRequests(
            holdingIdentityShortHash: ShortHash,
            requestSubjectX500Name: MemberX500Name?,
            viewHistoric: Boolean,
        ): Collection<RegistrationRequestStatus>

        fun reviewRegistrationRequest(
            holdingIdentityShortHash: ShortHash,
            requestId: UUID,
            approve: Boolean,
            reason: String?,
        )
    }

    private var impl: InnerMGMResourceClient = InactiveImpl

    private val coordinator = coordinatorFactory.createCoordinator<MGMResourceClient>(::processEvent)

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
        ttl: Instant?,
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

    override fun addApprovalRule(
        holdingIdentityShortHash: ShortHash, ruleParams: ApprovalRuleParams
    ) = impl.addApprovalRule(holdingIdentityShortHash, ruleParams)

    override fun getApprovalRules(holdingIdentityShortHash: ShortHash, ruleType: ApprovalRuleType) =
        impl.getApprovalRules(holdingIdentityShortHash, ruleType)

    override fun deleteApprovalRule(holdingIdentityShortHash: ShortHash, ruleId: String, ruleType: ApprovalRuleType) =
        impl.deleteApprovalRule(holdingIdentityShortHash, ruleId, ruleType)

    override fun viewRegistrationRequests(
        holdingIdentityShortHash: ShortHash, requestSubjectX500Name: MemberX500Name?, viewHistoric: Boolean
    ) = impl.viewRegistrationRequests(holdingIdentityShortHash, requestSubjectX500Name, viewHistoric)

    override fun reviewRegistrationRequest(
        holdingIdentityShortHash: ShortHash, requestId: UUID, approve: Boolean, reason: String?
    ) = impl.reviewRegistrationRequest(holdingIdentityShortHash, requestId, approve, reason)

    private fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                coordinator.createManagedResource(FOLLOW_CHANGES_RESOURCE_NAME) {
                    coordinator.followStatusChangesByName(
                        setOf(
                            LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                            LifecycleCoordinatorName.forComponent<MembershipGroupReaderProvider>(),
                            LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>(),
                            LifecycleCoordinatorName.forComponent<MembershipPersistenceClient>(),
                            LifecycleCoordinatorName.forComponent<MembershipQueryClient>(),
                        )
                    )
                }
            }
            is StopEvent -> {
                coordinator.closeManagedResources(
                    setOf(
                        FOLLOW_CHANGES_RESOURCE_NAME,
                        WAIT_FOR_CONFIG_RESOURCE_NAME,
                        PUBLISHER_RESOURCE_NAME,
                    )
                )
                deactivate("Handling the stop event for component.")
            }
            is RegistrationStatusChangeEvent -> {
                when (event.status) {
                    LifecycleStatus.UP -> {
                        coordinator.createManagedResource(WAIT_FOR_CONFIG_RESOURCE_NAME) {
                            configurationReadService.registerComponentForUpdates(
                                coordinator, setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG)
                            )
                        }
                    }
                    else -> {
                        coordinator.closeManagedResources(
                            setOf(
                                WAIT_FOR_CONFIG_RESOURCE_NAME,
                            )
                        )
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
                coordinator.createManagedResource(PUBLISHER_RESOURCE_NAME) {
                    publisherFactory.createPublisher(
                        messagingConfig = event.config.getConfig(ConfigKeys.MESSAGING_CONFIG),
                        publisherConfig = PublisherConfig(CLIENT_ID)
                    ).also {
                        it.start()
                    }
                }
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

    private object InactiveImpl : InnerMGMResourceClient {
        override fun generateGroupPolicy(holdingIdentityShortHash: ShortHash) =
            throw IllegalStateException(ERROR_MSG)

        override fun addApprovalRule(
            holdingIdentityShortHash: ShortHash,
            ruleParams: ApprovalRuleParams
        ) = throw IllegalStateException(ERROR_MSG)

        override fun getApprovalRules(holdingIdentityShortHash: ShortHash, ruleType: ApprovalRuleType) =
            throw IllegalStateException(ERROR_MSG)

        override fun deleteApprovalRule(holdingIdentityShortHash: ShortHash, ruleId: String, ruleType: ApprovalRuleType) =
            throw IllegalStateException(ERROR_MSG)

        override fun viewRegistrationRequests(
            holdingIdentityShortHash: ShortHash, requestSubjectX500Name: MemberX500Name?, viewHistoric: Boolean
        ) = throw IllegalStateException(ERROR_MSG)

        override fun reviewRegistrationRequest(
            holdingIdentityShortHash: ShortHash, requestId: UUID, approve: Boolean, reason: String?
        ) = throw IllegalStateException(ERROR_MSG)

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
            ttl: Instant?,
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
    ) : InnerMGMResourceClient {
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
            ttl: Instant?,
            remarks: String?
        ): PreAuthToken {
            val mgmHoldingIdentity = mgmHoldingIdentity(holdingIdentityShortHash)
            val tokenId = UUID.randomUUID()
            membershipPersistenceClient.generatePreAuthToken(mgmHoldingIdentity, tokenId, ownerX500Name, ttl, remarks)
                .getOrThrow()
            return PreAuthToken(tokenId.toString(), ownerX500Name.toString(), ttl, PreAuthTokenStatus.AVAILABLE, remarks, null)
        }

        override fun getPreAuthTokens(
            holdingIdentityShortHash: ShortHash,
            ownerX500Name: MemberX500Name?,
            preAuthTokenId: UUID?,
            viewInactive: Boolean
        ): Collection<PreAuthToken> {
            val mgmHoldingIdentity = mgmHoldingIdentity(holdingIdentityShortHash)
            return membershipQueryClient.queryPreAuthTokens(
                mgmHoldingIdentity,
                ownerX500Name,
                preAuthTokenId,
                viewInactive
            ).getOrThrow()
        }

        override fun revokePreAuthToken(holdingIdentityShortHash: ShortHash, preAuthTokenId: UUID, remarks: String?): PreAuthToken {
            val mgmHoldingIdentity = mgmHoldingIdentity(holdingIdentityShortHash)
            return membershipPersistenceClient.revokePreAuthToken(mgmHoldingIdentity, preAuthTokenId, remarks).getOrThrow()
        }

        override fun addApprovalRule(
            holdingIdentityShortHash: ShortHash, ruleParams: ApprovalRuleParams
        ): ApprovalRuleDetails =
            membershipPersistenceClient.addApprovalRule(
                mgmHoldingIdentity(holdingIdentityShortHash),
                ruleParams
            ).getOrThrow()

        override fun getApprovalRules(
            holdingIdentityShortHash: ShortHash, ruleType: ApprovalRuleType
        ): Collection<ApprovalRuleDetails> =
            membershipQueryClient.getApprovalRules(
                mgmHoldingIdentity(holdingIdentityShortHash),
                ruleType
            ).getOrThrow()

        override fun deleteApprovalRule(holdingIdentityShortHash: ShortHash, ruleId: String, ruleType: ApprovalRuleType) =
            membershipPersistenceClient.deleteApprovalRule(
                mgmHoldingIdentity(holdingIdentityShortHash),
                ruleId,
                ruleType
            ).getOrThrow()

        override fun viewRegistrationRequests(
            holdingIdentityShortHash: ShortHash, requestSubjectX500Name: MemberX500Name?, viewHistoric: Boolean
        ): Collection<RegistrationRequestStatus> {
            val statuses = if (viewHistoric) {
                listOf(RegistrationStatus.PENDING_MANUAL_APPROVAL, RegistrationStatus.APPROVED, RegistrationStatus.DECLINED)
            } else {
                listOf(RegistrationStatus.PENDING_MANUAL_APPROVAL)
            }
            return membershipQueryClient.queryRegistrationRequestsStatus(
                mgmHoldingIdentity(holdingIdentityShortHash),
                requestSubjectX500Name,
                statuses,
            ).getOrThrow()
        }

        override fun reviewRegistrationRequest(
            holdingIdentityShortHash: ShortHash, requestId: UUID, approve: Boolean, reason: String?
        ) {
            val mgm = mgmHoldingIdentity(holdingIdentityShortHash)
            val requestStatus =
                membershipQueryClient.queryRegistrationRequestStatus(mgm, requestId.toString()).getOrThrow()
                    ?: throw IllegalArgumentException(
                        "No request with registration request ID '$requestId' was found."
                    )
            require(requestStatus.status == RegistrationStatus.PENDING_MANUAL_APPROVAL) {
                "Registration request must be in ${RegistrationStatus.PENDING_MANUAL_APPROVAL} status to perform this action."
            }
            if (approve) {
                publishApprovalDecision(ApproveRegistration(), holdingIdentityShortHash, requestId.toString())
            } else {
                publishApprovalDecision(DeclineRegistration(reason ?: ""), holdingIdentityShortHash, requestId.toString())
            }
        }

        private fun publishApprovalDecision(command: Any, holdingIdentityShortHash: ShortHash, requestId: String) {
            coordinator.getManagedResource<Publisher>(PUBLISHER_RESOURCE_NAME)?.publish(
                listOf(
                    Record(
                        REGISTRATION_COMMAND_TOPIC,
                        "$requestId-${holdingIdentityShortHash}",
                        RegistrationCommand(command)
                    )
                )
            )?.forEach {
                it.join()
            }
        }

        override fun close() = rpcSender.close()

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
