package net.corda.membership.impl.client

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.core.ShortHash
import net.corda.data.KeyValuePairList
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.SignedData
import net.corda.data.membership.actions.request.DistributeGroupParameters
import net.corda.data.membership.actions.request.DistributeMemberInfo
import net.corda.data.membership.actions.request.MembershipActionsRequest
import net.corda.data.membership.command.registration.RegistrationCommand
import net.corda.data.membership.command.registration.mgm.ApproveRegistration
import net.corda.data.membership.command.registration.mgm.DeclineRegistration
import net.corda.data.membership.common.ApprovalRuleDetails
import net.corda.data.membership.common.ApprovalRuleType
import net.corda.data.membership.common.RegistrationRequestDetails
import net.corda.data.membership.common.v2.RegistrationStatus
import net.corda.data.membership.preauth.PreAuthToken
import net.corda.data.membership.preauth.PreAuthTokenStatus
import net.corda.data.membership.rpc.request.MGMGroupPolicyRequest
import net.corda.data.membership.rpc.request.MembershipRpcRequest
import net.corda.data.membership.rpc.request.MembershipRpcRequestContext
import net.corda.data.membership.rpc.response.MGMGroupPolicyResponse
import net.corda.data.membership.rpc.response.MembershipRpcResponse
import net.corda.data.p2p.app.MembershipStatusFilter
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
import net.corda.membership.client.CouldNotFindEntityException
import net.corda.membership.client.Entity
import net.corda.membership.client.MGMResourceClient
import net.corda.membership.client.MemberNotAnMgmException
import net.corda.membership.client.ServiceNotReadyException
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.EPOCH_KEY
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.MODIFIED_TIME_KEY
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.NOTARIES_KEY
import net.corda.membership.lib.InternalGroupParameters
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.id
import net.corda.membership.lib.MemberInfoExtension.Companion.isMgm
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.approval.ApprovalRuleParams
import net.corda.membership.lib.deserializeContext
import net.corda.membership.lib.registration.DECLINED_REASON_FOR_USER_GENERAL_MANUAL_DECLINED
import net.corda.membership.lib.toPersistentGroupParameters
import net.corda.membership.persistence.client.MembershipPersistenceClient
import net.corda.membership.persistence.client.MembershipQueryClient
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.schema.Schemas
import net.corda.schema.Schemas.Membership.GROUP_PARAMETERS_TOPIC
import net.corda.schema.Schemas.Membership.MEMBERSHIP_ACTIONS_TOPIC
import net.corda.schema.Schemas.Membership.MEMBER_LIST_TOPIC
import net.corda.schema.Schemas.Membership.REGISTRATION_COMMAND_TOPIC
import net.corda.schema.configuration.ConfigKeys
import net.corda.utilities.Either
import net.corda.utilities.concurrent.getOrThrow
import net.corda.utilities.debug
import net.corda.utilities.seconds
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeoutException

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
    @Reference(service = MemberInfoFactory::class)
    val memberInfoFactory: MemberInfoFactory,
    @Reference(service = KeyEncodingService::class)
    private val keyEncodingService: KeyEncodingService,
    @Reference(service = CordaAvroSerializationFactory::class)
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory,
) : MGMResourceClient {

    private companion object {
        const val ERROR_MSG = "Service is in an incorrect state for calling."
        const val CLIENT_ID = "mgm-resource-client"
        const val GROUP_NAME = "mgm-resource-client"
        const val FOLLOW_CHANGES_RESOURCE_NAME = "MGMResourceClient.followStatusChangesByName"
        const val WAIT_FOR_CONFIG_RESOURCE_NAME = "MGMResourceClient.registerComponentForUpdates"
        const val PUBLISHER_RESOURCE_NAME = "MGMResourceClient.publisher"
        const val FORCE_DECLINE_MESSAGE = "Force declined by MGM"

        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        val clock = UTCClock()
        val TIMEOUT = 10.seconds
    }

    private val deserializer: CordaAvroDeserializer<KeyValuePairList> by lazy {
        cordaAvroSerializationFactory.createAvroDeserializer(
            {
                logger.error("Failed to deserialize key value pair list.")
            },
            KeyValuePairList::class.java,
        )
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
            remarks: String?,
        ): PreAuthToken

        fun getPreAuthTokens(
            holdingIdentityShortHash: ShortHash,
            ownerX500Name: MemberX500Name?,
            preAuthTokenId: UUID?,
            viewInactive: Boolean,
        ): Collection<PreAuthToken>

        fun revokePreAuthToken(holdingIdentityShortHash: ShortHash, preAuthTokenId: UUID, remarks: String? = null): PreAuthToken
        fun addApprovalRule(
            holdingIdentityShortHash: ShortHash,
            ruleParams: ApprovalRuleParams,
        ): ApprovalRuleDetails

        fun getApprovalRules(holdingIdentityShortHash: ShortHash, ruleType: ApprovalRuleType): Collection<ApprovalRuleDetails>

        fun deleteApprovalRule(holdingIdentityShortHash: ShortHash, ruleId: String, ruleType: ApprovalRuleType)

        fun viewRegistrationRequests(
            holdingIdentityShortHash: ShortHash,
            requestSubjectX500Name: MemberX500Name?,
            viewHistoric: Boolean,
        ): Collection<RegistrationRequestDetails>

        fun reviewRegistrationRequest(
            holdingIdentityShortHash: ShortHash,
            requestId: UUID,
            approve: Boolean,
            reason: String?,
        )

        fun forceDeclineRegistrationRequest(
            holdingIdentityShortHash: ShortHash,
            requestId: UUID,
        )

        fun suspendMember(
            holdingIdentityShortHash: ShortHash,
            memberX500Name: MemberX500Name,
            serialNumber: Long?,
            reason: String?,
        )

        fun activateMember(
            holdingIdentityShortHash: ShortHash,
            memberX500Name: MemberX500Name,
            serialNumber: Long?,
            reason: String?,
        )

        fun updateGroupParameters(
            holdingIdentityShortHash: ShortHash,
            newGroupParameters: Map<String, String>,
        ): InternalGroupParameters
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
        holdingIdentityShortHash: ShortHash,
    ) = impl.mutualTlsListClientCertificate(
        holdingIdentityShortHash,
    )

    override fun generatePreAuthToken(
        holdingIdentityShortHash: ShortHash,
        ownerX500Name: MemberX500Name,
        ttl: Instant?,
        remarks: String?,
    ) = impl.generatePreAuthToken(holdingIdentityShortHash, ownerX500Name, ttl, remarks)

    override fun getPreAuthTokens(
        holdingIdentityShortHash: ShortHash,
        ownerX500Name: MemberX500Name?,
        preAuthTokenId: UUID?,
        viewInactive: Boolean,
    ) = impl.getPreAuthTokens(holdingIdentityShortHash, ownerX500Name, preAuthTokenId, viewInactive)

    override fun revokePreAuthToken(holdingIdentityShortHash: ShortHash, preAuthTokenId: UUID, remarks: String?) =
        impl.revokePreAuthToken(holdingIdentityShortHash, preAuthTokenId, remarks)

    override fun addApprovalRule(
        holdingIdentityShortHash: ShortHash,
        ruleParams: ApprovalRuleParams,
    ) = impl.addApprovalRule(holdingIdentityShortHash, ruleParams)

    override fun getApprovalRules(holdingIdentityShortHash: ShortHash, ruleType: ApprovalRuleType) =
        impl.getApprovalRules(holdingIdentityShortHash, ruleType)

    override fun deleteApprovalRule(holdingIdentityShortHash: ShortHash, ruleId: String, ruleType: ApprovalRuleType) =
        impl.deleteApprovalRule(holdingIdentityShortHash, ruleId, ruleType)

    override fun viewRegistrationRequests(
        holdingIdentityShortHash: ShortHash,
        requestSubjectX500Name: MemberX500Name?,
        viewHistoric: Boolean,
    ) = impl.viewRegistrationRequests(holdingIdentityShortHash, requestSubjectX500Name, viewHistoric)

    override fun reviewRegistrationRequest(
        holdingIdentityShortHash: ShortHash,
        requestId: UUID,
        approve: Boolean,
        reason: String?,
    ) = impl.reviewRegistrationRequest(holdingIdentityShortHash, requestId, approve, reason)

    override fun forceDeclineRegistrationRequest(holdingIdentityShortHash: ShortHash, requestId: UUID) =
        impl.forceDeclineRegistrationRequest(holdingIdentityShortHash, requestId)

    override fun suspendMember(
        holdingIdentityShortHash: ShortHash,
        memberX500Name: MemberX500Name,
        serialNumber: Long?,
        reason: String?,
    ) = impl.suspendMember(holdingIdentityShortHash, memberX500Name, serialNumber, reason)

    override fun activateMember(
        holdingIdentityShortHash: ShortHash,
        memberX500Name: MemberX500Name,
        serialNumber: Long?,
        reason: String?,
    ) = impl.activateMember(holdingIdentityShortHash, memberX500Name, serialNumber, reason)

    override fun updateGroupParameters(
        holdingIdentityShortHash: ShortHash,
        newGroupParameters: Map<String, String>,
    ) = impl.updateGroupParameters(holdingIdentityShortHash, newGroupParameters)

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
                        ),
                    )
                }
            }
            is StopEvent -> {
                coordinator.closeManagedResources(
                    setOf(
                        FOLLOW_CHANGES_RESOURCE_NAME,
                        WAIT_FOR_CONFIG_RESOURCE_NAME,
                        PUBLISHER_RESOURCE_NAME,
                    ),
                )
                deactivate("Handling the stop event for component.")
            }
            is RegistrationStatusChangeEvent -> {
                when (event.status) {
                    LifecycleStatus.UP -> {
                        coordinator.createManagedResource(WAIT_FOR_CONFIG_RESOURCE_NAME) {
                            configurationReadService.registerComponentForUpdates(
                                coordinator,
                                setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG),
                            )
                        }
                    }
                    else -> {
                        coordinator.closeManagedResources(
                            setOf(
                                WAIT_FOR_CONFIG_RESOURCE_NAME,
                            ),
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
                            responseType = MembershipRpcResponse::class.java,
                        ),
                        event.config.getConfig(ConfigKeys.MESSAGING_CONFIG),
                    ).also {
                        it.start()
                    },
                )
                coordinator.createManagedResource(PUBLISHER_RESOURCE_NAME) {
                    publisherFactory.createPublisher(
                        messagingConfig = event.config.getConfig(ConfigKeys.MESSAGING_CONFIG),
                        publisherConfig = PublisherConfig(CLIENT_ID),
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
            ruleParams: ApprovalRuleParams,
        ) = throw IllegalStateException(ERROR_MSG)

        override fun getApprovalRules(holdingIdentityShortHash: ShortHash, ruleType: ApprovalRuleType) =
            throw IllegalStateException(ERROR_MSG)

        override fun deleteApprovalRule(holdingIdentityShortHash: ShortHash, ruleId: String, ruleType: ApprovalRuleType) =
            throw IllegalStateException(ERROR_MSG)

        override fun viewRegistrationRequests(
            holdingIdentityShortHash: ShortHash,
            requestSubjectX500Name: MemberX500Name?,
            viewHistoric: Boolean,
        ) = throw IllegalStateException(ERROR_MSG)

        override fun reviewRegistrationRequest(
            holdingIdentityShortHash: ShortHash,
            requestId: UUID,
            approve: Boolean,
            reason: String?,
        ) = throw IllegalStateException(ERROR_MSG)

        override fun forceDeclineRegistrationRequest(
            holdingIdentityShortHash: ShortHash,
            requestId: UUID,
        ) = throw IllegalStateException(ERROR_MSG)

        override fun suspendMember(
            holdingIdentityShortHash: ShortHash,
            memberX500Name: MemberX500Name,
            serialNumber: Long?,
            reason: String?,
        ) = throw IllegalStateException(ERROR_MSG)

        override fun activateMember(
            holdingIdentityShortHash: ShortHash,
            memberX500Name: MemberX500Name,
            serialNumber: Long?,
            reason: String?,
        ) = throw IllegalStateException(ERROR_MSG)

        override fun updateGroupParameters(
            holdingIdentityShortHash: ShortHash,
            newGroupParameters: Map<String, String>,
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
            remarks: String?,
        ) = throw IllegalStateException(ERROR_MSG)

        override fun getPreAuthTokens(
            holdingIdentityShortHash: ShortHash,
            ownerX500Name: MemberX500Name?,
            preAuthTokenId: UUID?,
            viewInactive: Boolean,
        ) = throw IllegalStateException(ERROR_MSG)

        override fun revokePreAuthToken(holdingIdentityShortHash: ShortHash, preAuthTokenId: UUID, remarks: String?) =
            throw IllegalStateException(ERROR_MSG)

        override fun close() = Unit
    }

    private inner class ActiveImpl(
        val rpcSender: RPCSender<MembershipRpcRequest, MembershipRpcResponse>,
    ) : InnerMGMResourceClient {
        @Suppress("ThrowsCount")
        fun mgmHoldingIdentity(holdingIdentityShortHash: ShortHash): HoldingIdentity {
            val holdingIdentity =
                virtualNodeInfoReadService.getByHoldingIdentityShortHash(holdingIdentityShortHash)?.holdingIdentity
                    ?: throw CouldNotFindEntityException(Entity.VIRTUAL_NODE, holdingIdentityShortHash)

            val reader = membershipGroupReaderProvider.getGroupReader(holdingIdentity)

            val filteredMembers =
                reader.lookup(holdingIdentity.x500Name)
                    ?: throw CouldNotFindEntityException(Entity.MEMBER, holdingIdentityShortHash)

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
                    clock.instant(),
                ),
                MGMGroupPolicyRequest(holdingIdentityShortHash.toString()),
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
            remarks: String?,
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
            viewInactive: Boolean,
        ): Collection<PreAuthToken> {
            val mgmHoldingIdentity = mgmHoldingIdentity(holdingIdentityShortHash)
            return membershipQueryClient.queryPreAuthTokens(
                mgmHoldingIdentity,
                ownerX500Name,
                preAuthTokenId,
                viewInactive,
            ).getOrThrow()
        }

        override fun revokePreAuthToken(holdingIdentityShortHash: ShortHash, preAuthTokenId: UUID, remarks: String?): PreAuthToken {
            val mgmHoldingIdentity = mgmHoldingIdentity(holdingIdentityShortHash)
            return membershipPersistenceClient.revokePreAuthToken(mgmHoldingIdentity, preAuthTokenId, remarks).getOrThrow()
        }

        override fun addApprovalRule(
            holdingIdentityShortHash: ShortHash,
            ruleParams: ApprovalRuleParams,
        ): ApprovalRuleDetails =
            membershipPersistenceClient.addApprovalRule(
                mgmHoldingIdentity(holdingIdentityShortHash),
                ruleParams,
            ).getOrThrow()

        override fun getApprovalRules(
            holdingIdentityShortHash: ShortHash,
            ruleType: ApprovalRuleType,
        ): Collection<ApprovalRuleDetails> =
            membershipQueryClient.getApprovalRules(
                mgmHoldingIdentity(holdingIdentityShortHash),
                ruleType,
            ).getOrThrow()

        override fun deleteApprovalRule(holdingIdentityShortHash: ShortHash, ruleId: String, ruleType: ApprovalRuleType) =
            membershipPersistenceClient.deleteApprovalRule(
                mgmHoldingIdentity(holdingIdentityShortHash),
                ruleId,
                ruleType,
            ).getOrThrow()

        override fun viewRegistrationRequests(
            holdingIdentityShortHash: ShortHash,
            requestSubjectX500Name: MemberX500Name?,
            viewHistoric: Boolean,
        ): Collection<RegistrationRequestDetails> {
            val statuses = if (viewHistoric) {
                listOf(RegistrationStatus.PENDING_MANUAL_APPROVAL, RegistrationStatus.APPROVED, RegistrationStatus.DECLINED)
            } else {
                listOf(RegistrationStatus.PENDING_MANUAL_APPROVAL)
            }
            return membershipQueryClient.queryRegistrationRequests(
                mgmHoldingIdentity(holdingIdentityShortHash),
                requestSubjectX500Name,
                statuses,
            ).getOrThrow()
        }

        override fun reviewRegistrationRequest(
            holdingIdentityShortHash: ShortHash,
            requestId: UUID,
            approve: Boolean,
            reason: String?,
        ) {
            val mgm = mgmHoldingIdentity(holdingIdentityShortHash)
            val requestStatus =
                membershipQueryClient.queryRegistrationRequest(mgm, requestId.toString()).getOrThrow()
                    ?: throw IllegalArgumentException(
                        "No request with registration request ID '$requestId' was found.",
                    )
            require(requestStatus.registrationStatus == RegistrationStatus.PENDING_MANUAL_APPROVAL) {
                "Registration request must be in ${RegistrationStatus.PENDING_MANUAL_APPROVAL} status to perform this action."
            }
            val memberName = findMemberName(requestStatus.memberProvidedContext)
            if (approve) {
                publishRegistrationCommand(ApproveRegistration(), memberName, mgm.groupId)
            } else {
                publishRegistrationCommand(
                    DeclineRegistration(
                        reason ?: "",
                        DECLINED_REASON_FOR_USER_GENERAL_MANUAL_DECLINED,
                    ),
                    memberName,
                    mgm.groupId,
                )
            }
        }

        override fun forceDeclineRegistrationRequest(holdingIdentityShortHash: ShortHash, requestId: UUID) {
            val mgm = mgmHoldingIdentity(holdingIdentityShortHash)
            val requestStatus = membershipQueryClient.queryRegistrationRequest(
                mgm,
                requestId.toString(),
            ).getOrThrow()
                ?: throw IllegalArgumentException("No request with registration request ID '$requestId' was found.")

            logger.info("Force declining registration request with ID='$requestId' and status='${requestStatus.registrationStatus}'.")

            require(!setOf(RegistrationStatus.APPROVED, RegistrationStatus.DECLINED).contains(requestStatus.registrationStatus)) {
                "The registration process for request '$requestId' has been completed, so this request cannot be force " +
                    "declined. Refer to the docs on Member Suspension to suspend approved members."
            }

            publishRegistrationCommand(
                DeclineRegistration(FORCE_DECLINE_MESSAGE, DECLINED_REASON_FOR_USER_GENERAL_MANUAL_DECLINED),
                findMemberName(requestStatus.memberProvidedContext),
                mgm.groupId,
            )
        }

        override fun suspendMember(
            holdingIdentityShortHash: ShortHash,
            memberX500Name: MemberX500Name,
            serialNumber: Long?,
            reason: String?,
        ) {
            val (mgm, memberShortHash) = validateSuspensionActivationRequest(holdingIdentityShortHash, memberX500Name)
            val (updatedMemberInfo, updatedGroupParameters) = membershipPersistenceClient.suspendMember(
                mgm,
                memberX500Name,
                serialNumber,
                reason,
            ).getOrThrow()
            publishSuspensionActivationRecords(
                updatedMemberInfo,
                updatedGroupParameters,
                memberX500Name,
                memberShortHash,
                mgm,
                holdingIdentityShortHash.value,
            )
        }

        override fun activateMember(
            holdingIdentityShortHash: ShortHash,
            memberX500Name: MemberX500Name,
            serialNumber: Long?,
            reason: String?,
        ) {
            val (mgm, memberShortHash) = validateSuspensionActivationRequest(holdingIdentityShortHash, memberX500Name)
            val (updatedMemberInfo, updatedGroupParameters) = membershipPersistenceClient.activateMember(
                mgm,
                memberX500Name,
                serialNumber,
                reason,
            ).getOrThrow()
            publishSuspensionActivationRecords(
                updatedMemberInfo,
                updatedGroupParameters,
                memberX500Name,
                memberShortHash,
                mgm,
                holdingIdentityShortHash.value,
            )
        }

        override fun updateGroupParameters(
            holdingIdentityShortHash: ShortHash,
            newGroupParameters: Map<String, String>,
        ): InternalGroupParameters {
            val mgm = mgmHoldingIdentity(holdingIdentityShortHash)

            membershipGroupReaderProvider.getGroupReader(mgm).groupParameters.let { current ->
                val changeableParameters = current?.toMap()?.filterNot {
                    it.key in setOf(EPOCH_KEY, MODIFIED_TIME_KEY) || it.key.startsWith(NOTARIES_KEY)
                }
                if (newGroupParameters == changeableParameters) {
                    logger.info(
                        "Nothing to persist - submitted group parameters are identical to the existing group " +
                            "parameters.",
                    )
                    return current
                }
            }

            val updatedParameters = membershipPersistenceClient.updateGroupParameters(
                mgm,
                newGroupParameters,
            ).getOrThrow()

            createDistributionRequest(mgm, updatedParameters.epoch)

            return updatedParameters
        }

        private fun findMemberName(memberContext: SignedData): String {
            return memberContext.data.array().deserializeContext(deserializer)[PARTY_NAME]
                ?: throw IllegalArgumentException("Member name must be defined.")
        }

        private fun createDistributionRequest(mgm: HoldingIdentity, epoch: Int) {
            val distributionRequest = MembershipActionsRequest(
                DistributeGroupParameters(
                    mgm.toAvro(),
                    epoch,
                ),
            )
            coordinator.getManagedResource<Publisher>(PUBLISHER_RESOURCE_NAME)?.apply {
                publish(
                    listOf(
                        Record(
                            topic = MEMBERSHIP_ACTIONS_TOPIC,
                            key = "${mgm.x500Name}-${mgm.groupId}",
                            value = distributionRequest,
                        ),
                    ),
                ).forEach { it.join() }
            }
        }

        private fun validateSuspensionActivationRequest(
            holdingIdentityShortHash: ShortHash,
            memberX500Name: MemberX500Name,
        ): Pair<HoldingIdentity, String> {
            val mgm = mgmHoldingIdentity(holdingIdentityShortHash)
            val memberShortHash = membershipGroupReaderProvider.getGroupReader(mgm)
                .lookup(memberX500Name, MembershipStatusFilter.ACTIVE_OR_SUSPENDED)?.let {
                    require(!it.isMgm) { "This action may not be performed on the MGM." }
                    it.id
                } ?: throw NoSuchElementException("Member '$memberX500Name' not found.")
            return mgm to memberShortHash
        }

        /**
         * Publish the updated member info and request distribution via the message bus. Also, optionally publish the updated group
         * parameters.
         */
        private fun publishSuspensionActivationRecords(
            memberInfo: PersistentMemberInfo,
            groupParameters: InternalGroupParameters?,
            memberX500Name: MemberX500Name,
            memberShortHash: String,
            mgmHoldingIdentity: HoldingIdentity,
            mgmShortHash: String,
        ) {
            val serialNumber = memberInfoFactory.createMemberInfo(memberInfo).serial
            val publisher = coordinator.getManagedResource<Publisher>(PUBLISHER_RESOURCE_NAME)
            val recordForGroupParameters = groupParameters?.let {
                listOf(
                    Record(
                        GROUP_PARAMETERS_TOPIC,
                        mgmHoldingIdentity.shortHash.toString(),
                        it.toPersistentGroupParameters(mgmHoldingIdentity, keyEncodingService),
                    ),
                )
            } ?: emptyList()
            val distributionRequest = MembershipActionsRequest(
                DistributeMemberInfo(
                    mgmHoldingIdentity.toAvro(),
                    HoldingIdentity(memberX500Name, mgmHoldingIdentity.groupId).toAvro(),
                    groupParameters?.epoch,
                    serialNumber,
                ),
            )
            try {
                publisher?.publish(
                    listOf(
                        Record(topic = MEMBER_LIST_TOPIC, key = "$mgmShortHash-$memberShortHash", value = memberInfo),
                        Record(
                            topic = MEMBERSHIP_ACTIONS_TOPIC,
                            key = "$memberX500Name-${mgmHoldingIdentity.groupId}",
                            value = distributionRequest,
                        ),
                    ) + recordForGroupParameters,
                )
            } catch (e: CordaMessageAPIIntermittentException) {
                // As long as the database update was successful, ignore any failures while publishing to Kafka.
            }
        }

        private fun publishRegistrationCommand(command: Any, memberName: String, groupId: String) {
            coordinator.getManagedResource<Publisher>(PUBLISHER_RESOURCE_NAME)?.publish(
                listOf(
                    Record(
                        REGISTRATION_COMMAND_TOPIC,
                        "$memberName-$groupId",
                        RegistrationCommand(command),
                    ),
                ),
            )?.forEach {
                it.join()
            }
        }

        override fun close() = rpcSender.close()

        @Suppress("SpreadOperator")
        private fun generateGroupPolicyResponse(response: MGMGroupPolicyResponse): String =
            response.groupPolicy.toString()

        @Suppress("ThrowsCount")
        private inline fun <reified RESPONSE> MembershipRpcRequest.sendRequest(): RESPONSE {
            logger.debug { "Sending request: $this" }
            val response = try {
                rpcSender.sendRequest(this).getOrThrow(TIMEOUT)
            } catch (e: TimeoutException) {
                throw ServiceNotReadyException(e)
            }
            return when (val validated = validate<RESPONSE>(response)) {
                is Either.Left -> throw CordaRuntimeException(
                    "Failed to send request and receive response for MGM RPC operation. ${validated.a}",
                )
                is Either.Right -> validated.b
            }
        }

        private inline fun <reified RESPONSE> MembershipRpcRequest.validate(
            rpcResponse: MembershipRpcResponse?,
        ): Either<String, RESPONSE> {
            return if (rpcResponse?.responseContext == null || rpcResponse.response == null) {
                Either.Left("Response cannot be null.")
            } else if (this.requestContext.requestId != rpcResponse.responseContext.requestId) {
                Either.Left("Request ID must match in the request and response.")
            } else if (this.requestContext.requestTimestamp != rpcResponse.responseContext.requestTimestamp) {
                Either.Left("Request timestamp must match in the request and response.")
            } else {
                val response = rpcResponse.response as? RESPONSE
                if (response == null) {
                    Either.Left(
                        "Expected ${RESPONSE::class.java} as response type, but received ${rpcResponse.response.javaClass}.",
                    )
                } else {
                    Either.Right(
                        response,
                    )
                }
            }
        }
    }
}
