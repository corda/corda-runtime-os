package net.corda.membership.impl.rest.v1

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.configuration.read.ConfigurationGetService
import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.core.ShortHash
import net.corda.data.KeyValuePairList
import net.corda.data.membership.common.ApprovalRuleDetails
import net.corda.data.membership.common.ApprovalRuleType
import net.corda.data.membership.common.ApprovalRuleType.PREAUTH
import net.corda.data.membership.common.ApprovalRuleType.STANDARD
import net.corda.data.membership.common.RegistrationRequestDetails
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.rest.PluggableRestResource
import net.corda.rest.exception.BadRequestException
import net.corda.rest.exception.InvalidInputDataException
import net.corda.rest.exception.ResourceNotFoundException
import net.corda.rest.exception.ServiceUnavailableException
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.membership.client.CouldNotFindEntityException
import net.corda.membership.client.MGMResourceClient
import net.corda.membership.client.MemberNotAnMgmException
import net.corda.membership.rest.v1.MGMRestResource
import net.corda.membership.rest.v1.types.request.ApprovalRuleRequestParams
import net.corda.membership.rest.v1.types.request.PreAuthTokenRequest
import net.corda.membership.rest.v1.types.request.ManualDeclinationReason
import net.corda.membership.rest.v1.types.response.ApprovalRuleInfo
import net.corda.membership.rest.v1.types.response.PreAuthToken
import net.corda.membership.rest.v1.types.response.PreAuthTokenStatus
import net.corda.membership.impl.rest.v1.lifecycle.RestResourceLifecycleHandler
import net.corda.membership.lib.ContextDeserializationException
import net.corda.membership.rest.v1.types.response.MemberInfoSubmitted
import net.corda.membership.rest.v1.types.response.RestRegistrationRequestStatus
import net.corda.membership.rest.v1.types.response.RegistrationStatus
import net.corda.membership.lib.approval.ApprovalRuleParams
import net.corda.membership.lib.deserializeContext
import net.corda.membership.lib.exceptions.InvalidEntityUpdateException
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsType
import net.corda.membership.lib.verifiers.GroupParametersUpdateVerifier
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.membership.rest.v1.types.RestGroupParameters
import net.corda.membership.rest.v1.types.request.SuspensionActivationParameters
import net.corda.messaging.api.exception.CordaRPCAPIPartitionException
import net.corda.rest.exception.InternalServerException
import net.corda.rest.exception.InvalidStateChangeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.read.rest.extensions.parseOrThrow
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.regex.PatternSyntaxException
import javax.persistence.PessimisticLockException
import net.corda.data.membership.preauth.PreAuthToken as AvroPreAuthToken
import net.corda.data.membership.preauth.PreAuthTokenStatus as AvroPreAuthTokenStatus

@Suppress("TooManyFunctions", "LongParameterList")
@Component(service = [PluggableRestResource::class])
class MGMRestResourceImpl internal constructor(
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val mgmResourceClient: MGMResourceClient,
    private val configurationGetService: ConfigurationGetService,
    private val clock: Clock = UTCClock(),
    private val platformInfoProvider: PlatformInfoProvider,
) : MGMRestResource, PluggableRestResource<MGMRestResource>, Lifecycle {

    @Activate
    constructor(
        @Reference(service = CordaAvroSerializationFactory::class)
        cordaAvroSerializationFactory: CordaAvroSerializationFactory,
        @Reference(service = LifecycleCoordinatorFactory::class)
        coordinatorFactory: LifecycleCoordinatorFactory,
        @Reference(service = MGMResourceClient::class)
        mgmResourceClient: MGMResourceClient,
        @Reference(service = ConfigurationGetService::class)
        configurationGetService: ConfigurationGetService,
        @Reference(service = PlatformInfoProvider::class)
        platformInfoProvider: PlatformInfoProvider
    ) : this(
        cordaAvroSerializationFactory,
        coordinatorFactory,
        mgmResourceClient,
        configurationGetService,
        UTCClock(),
        platformInfoProvider
    )

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val deserializer: CordaAvroDeserializer<KeyValuePairList> =
        cordaAvroSerializationFactory.createAvroDeserializer(
            {
                logger.error("Failed to deserialize key value pair list.")
            },
            KeyValuePairList::class.java
        )

    private interface InnerMGMRestResource {
        fun generateGroupPolicy(
            holdingIdentityShortHash: String
        ): String

        fun mutualTlsAllowClientCertificate(
            holdingIdentityShortHash: String,
            subject: String,
        )

        fun mutualTlsDisallowClientCertificate(
            holdingIdentityShortHash: String,
            subject: String,
        )

        fun mutualTlsListClientCertificate(
            holdingIdentityShortHash: String,
        ): Collection<String>

        fun generatePreAuthToken(
            holdingIdentityShortHash: String,
            request: PreAuthTokenRequest
        ): PreAuthToken

        fun getPreAuthTokens(
            holdingIdentityShortHash: String,
            ownerX500Name: String?,
            preAuthTokenId: String?,
            viewInactive: Boolean
        ): Collection<PreAuthToken>

        fun revokePreAuthToken(
            holdingIdentityShortHash: String,
            preAuthTokenId: String,
            remarks: String? = null
        ): PreAuthToken

        fun addGroupApprovalRule(
            holdingIdentityShortHash: String,
            ruleInfo: ApprovalRuleRequestParams
        ): ApprovalRuleInfo

        fun getGroupApprovalRules(
            holdingIdentityShortHash: String
        ): Collection<ApprovalRuleInfo>

        fun deleteGroupApprovalRule(
            holdingIdentityShortHash: String,
            ruleId: String
        )

        fun addPreAuthGroupApprovalRule(
            holdingIdentityShortHash: String,
            ruleInfo: ApprovalRuleRequestParams
        ): ApprovalRuleInfo

        fun getPreAuthGroupApprovalRules(
            holdingIdentityShortHash: String
        ): Collection<ApprovalRuleInfo>

        fun deletePreAuthGroupApprovalRule(
            holdingIdentityShortHash: String,
            ruleId: String
        )

        fun viewRegistrationRequests(
            holdingIdentityShortHash: String,
            requestSubjectX500Name: String?,
            viewHistoric: Boolean,
        ): Collection<RestRegistrationRequestStatus>

        fun approveRegistrationRequest(holdingIdentityShortHash: String, requestId: String)

        fun declineRegistrationRequest(holdingIdentityShortHash: String, requestId: String, reason: ManualDeclinationReason)

        fun suspendMember(holdingIdentityShortHash: String, suspensionParams: SuspensionActivationParameters)

        fun activateMember(holdingIdentityShortHash: String, activationParams: SuspensionActivationParameters)

        fun updateGroupParameters(holdingIdentityShortHash: String, newGroupParameters: RestGroupParameters): RestGroupParameters
    }

    override val protocolVersion get() = platformInfoProvider.localWorkerPlatformVersion

    private var impl: InnerMGMRestResource = InactiveImpl

    private val coordinatorName = LifecycleCoordinatorName.forComponent<MGMRestResource>(
        protocolVersion.toString()
    )

    private val lifecycleHandler = RestResourceLifecycleHandler(
        ::activate,
        ::deactivate,
        setOf(
            LifecycleCoordinatorName.forComponent<MGMResourceClient>(),
            LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
        )
    )

    private val coordinator = coordinatorFactory.createCoordinator(coordinatorName, lifecycleHandler)

    override val targetInterface: Class<MGMRestResource> = MGMRestResource::class.java

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }

    override fun generateGroupPolicy(holdingIdentityShortHash: String) =
        impl.generateGroupPolicy(holdingIdentityShortHash)

    override fun mutualTlsAllowClientCertificate(holdingIdentityShortHash: String, subject: String) =
        impl.mutualTlsAllowClientCertificate(holdingIdentityShortHash, subject)

    override fun mutualTlsDisallowClientCertificate(holdingIdentityShortHash: String, subject: String) =
        impl.mutualTlsDisallowClientCertificate(holdingIdentityShortHash, subject)

    override fun mutualTlsListClientCertificate(holdingIdentityShortHash: String) =
        impl.mutualTlsListClientCertificate(holdingIdentityShortHash)

    override fun generatePreAuthToken(holdingIdentityShortHash: String, request: PreAuthTokenRequest) =
        impl.generatePreAuthToken(holdingIdentityShortHash, request)

    override fun getPreAuthTokens(
        holdingIdentityShortHash: String,
        ownerX500Name: String?,
        preAuthTokenId: String?,
        viewInactive: Boolean
    ) = impl.getPreAuthTokens(holdingIdentityShortHash, ownerX500Name, preAuthTokenId, viewInactive)

    override fun revokePreAuthToken(holdingIdentityShortHash: String, preAuthTokenId: String, remarks: String?) =
        impl.revokePreAuthToken(holdingIdentityShortHash, preAuthTokenId, remarks)

    override fun addGroupApprovalRule(holdingIdentityShortHash: String, ruleParams: ApprovalRuleRequestParams) =
        impl.addGroupApprovalRule(holdingIdentityShortHash, ruleParams)

    override fun getGroupApprovalRules(holdingIdentityShortHash: String) =
        impl.getGroupApprovalRules(holdingIdentityShortHash)

    override fun deleteGroupApprovalRule(holdingIdentityShortHash: String, ruleId: String) =
        impl.deleteGroupApprovalRule(holdingIdentityShortHash, ruleId)

    override fun addPreAuthGroupApprovalRule(holdingIdentityShortHash: String, ruleParams: ApprovalRuleRequestParams) =
        impl.addPreAuthGroupApprovalRule(holdingIdentityShortHash, ruleParams)

    override fun getPreAuthGroupApprovalRules(holdingIdentityShortHash: String) =
        impl.getPreAuthGroupApprovalRules(holdingIdentityShortHash)

    override fun deletePreAuthGroupApprovalRule(holdingIdentityShortHash: String, ruleId: String) =
        impl.deletePreAuthGroupApprovalRule(holdingIdentityShortHash, ruleId)

    override fun viewRegistrationRequests(
        holdingIdentityShortHash: String, requestSubjectX500Name: String?, viewHistoric: Boolean
    ) = impl.viewRegistrationRequests(holdingIdentityShortHash, requestSubjectX500Name, viewHistoric)

    override fun approveRegistrationRequest(
        holdingIdentityShortHash: String, requestId: String
    ) = impl.approveRegistrationRequest(holdingIdentityShortHash, requestId)

    override fun declineRegistrationRequest(
        holdingIdentityShortHash: String, requestId: String, reason: ManualDeclinationReason
    ) = impl.declineRegistrationRequest(holdingIdentityShortHash, requestId, reason)

    @Deprecated("Deprecated in favour of suspendMember")
    override fun deprecatedSuspendMember(holdingIdentityShortHash: String, suspensionParams: SuspensionActivationParameters) =
        impl.suspendMember(holdingIdentityShortHash, suspensionParams)

    override fun suspendMember(holdingIdentityShortHash: String, suspensionParams: SuspensionActivationParameters) =
        impl.suspendMember(holdingIdentityShortHash, suspensionParams.throwBadRequestIfNoSerialNumber())

    @Deprecated("Deprecated in favour of activateMember")
    override fun deprecatedActivateMember(holdingIdentityShortHash: String, activationParams: SuspensionActivationParameters) =
        impl.activateMember(holdingIdentityShortHash, activationParams)

    override fun activateMember(holdingIdentityShortHash: String, activationParams: SuspensionActivationParameters) =
        impl.activateMember(holdingIdentityShortHash, activationParams.throwBadRequestIfNoSerialNumber())

    override fun updateGroupParameters(holdingIdentityShortHash: String, newGroupParameters: RestGroupParameters) =
        impl.updateGroupParameters(holdingIdentityShortHash, newGroupParameters)

    fun activate(reason: String) {
        impl = ActiveImpl()
        coordinator.updateStatus(LifecycleStatus.UP, reason)
    }

    fun deactivate(reason: String) {
        coordinator.updateStatus(LifecycleStatus.DOWN, reason)
        impl = InactiveImpl
    }

    private fun SuspensionActivationParameters.throwBadRequestIfNoSerialNumber(): SuspensionActivationParameters {
        if (this.serialNumber == null) {
            throw BadRequestException("The serial number must be provided in the request.")
        }
        return this
    }

    private object InactiveImpl : InnerMGMRestResource {

        private val NOT_RUNNING_ERROR = "${MGMRestResourceImpl::class.java.simpleName} is not running. " +
                "Operation cannot be fulfilled."

        override fun generateGroupPolicy(
            holdingIdentityShortHash: String
        ): String = throwNotRunningException()

        override fun mutualTlsAllowClientCertificate(
            holdingIdentityShortHash: String,
            subject: String,
        ): Nothing = throwNotRunningException()

        override fun mutualTlsDisallowClientCertificate(
            holdingIdentityShortHash: String,
            subject: String,
        ): Nothing = throwNotRunningException()

        override fun mutualTlsListClientCertificate(
            holdingIdentityShortHash: String
        ): Collection<String> = throwNotRunningException()

        override fun addGroupApprovalRule(
            holdingIdentityShortHash: String,
            ruleInfo: ApprovalRuleRequestParams
        ): ApprovalRuleInfo = throwNotRunningException()

        override fun getGroupApprovalRules(
            holdingIdentityShortHash: String
        ): Collection<ApprovalRuleInfo> = throwNotRunningException()

        override fun deleteGroupApprovalRule(
            holdingIdentityShortHash: String,
            ruleId: String
        ): Nothing = throwNotRunningException()

        override fun addPreAuthGroupApprovalRule(
            holdingIdentityShortHash: String,
            ruleInfo: ApprovalRuleRequestParams
        ): ApprovalRuleInfo = throwNotRunningException()

        override fun getPreAuthGroupApprovalRules(
            holdingIdentityShortHash: String
        ): Collection<ApprovalRuleInfo> = throwNotRunningException()

        override fun deletePreAuthGroupApprovalRule(
            holdingIdentityShortHash: String,
            ruleId: String
        ): Nothing = throwNotRunningException()

        override fun generatePreAuthToken(
            holdingIdentityShortHash: String,
            request: PreAuthTokenRequest
        ): PreAuthToken = throwNotRunningException()

        override fun getPreAuthTokens(
            holdingIdentityShortHash: String,
            ownerX500Name: String?,
            preAuthTokenId: String?,
            viewInactive: Boolean
        ): Collection<PreAuthToken> = throwNotRunningException()

        override fun revokePreAuthToken(
            holdingIdentityShortHash: String,
            preAuthTokenId: String,
            remarks: String?
        ): PreAuthToken = throwNotRunningException()

        override fun viewRegistrationRequests(
            holdingIdentityShortHash: String,
            requestSubjectX500Name: String?,
            viewHistoric: Boolean,
        ): Collection<RestRegistrationRequestStatus> = throwNotRunningException()

        override fun approveRegistrationRequest(holdingIdentityShortHash: String, requestId: String): Unit =
            throwNotRunningException()

        override fun declineRegistrationRequest(
            holdingIdentityShortHash: String, requestId: String, reason: ManualDeclinationReason
        ): Unit = throwNotRunningException()

        override fun suspendMember(
            holdingIdentityShortHash: String,
            suspensionParams: SuspensionActivationParameters
        ): Unit = throwNotRunningException()

        override fun activateMember(
            holdingIdentityShortHash: String,
            activationParams: SuspensionActivationParameters
        ): Unit = throwNotRunningException()

        override fun updateGroupParameters(
            holdingIdentityShortHash: String,
            newGroupParameters: RestGroupParameters,
        ): RestGroupParameters = throwNotRunningException()

        private fun <T> throwNotRunningException(): T {
            throw ServiceUnavailableException(NOT_RUNNING_ERROR)
        }
    }

    private inner class ActiveImpl : InnerMGMRestResource {

        override fun generateGroupPolicy(
            holdingIdentityShortHash: String
        ) = handleCommonErrors(holdingIdentityShortHash) {
            mgmResourceClient.generateGroupPolicy(it)
        }

        override fun mutualTlsAllowClientCertificate(
            holdingIdentityShortHash: String,
            subject: String
        ) {
            verifyMutualTlsIsRunning()
            val subjectName = parseX500Name("subject", subject)
            handleCommonErrors(holdingIdentityShortHash) {
                mgmResourceClient.mutualTlsAllowClientCertificate(it, subjectName)
            }
        }

        override fun mutualTlsDisallowClientCertificate(holdingIdentityShortHash: String, subject: String) {
            verifyMutualTlsIsRunning()
            val subjectName = parseX500Name("subject", subject)
            handleCommonErrors(holdingIdentityShortHash) {
                mgmResourceClient.mutualTlsDisallowClientCertificate(it, subjectName)
            }
        }

        override fun mutualTlsListClientCertificate(holdingIdentityShortHash: String): Collection<String> {
            verifyMutualTlsIsRunning()
            return handleCommonErrors(holdingIdentityShortHash) {
                mgmResourceClient.mutualTlsListClientCertificate(it)
            }.map { it.toString() }
        }

        override fun generatePreAuthToken(
            holdingIdentityShortHash: String,
            request: PreAuthTokenRequest
        ): PreAuthToken {
            val ttlAsInstant = request.ttl?.let { ttl ->
                clock.instant() + ttl
            }
            val x500Name = parseX500Name("ownerX500Name", request.ownerX500Name)
            return handleCommonErrors(holdingIdentityShortHash) { shortHash ->
                mgmResourceClient.generatePreAuthToken(
                    shortHash,
                    x500Name,
                    ttlAsInstant,
                    request.remarks
                )
            }.fromAvro()
        }

        override fun getPreAuthTokens(
            holdingIdentityShortHash: String,
            ownerX500Name: String?,
            preAuthTokenId: String?,
            viewInactive: Boolean
        ): Collection<PreAuthToken> {
            val ownerX500 = ownerX500Name?.let {
                parseX500Name("ownerX500Name", it)
            }
            val tokenId = preAuthTokenId?.let {
                parsePreAuthTokenId(it)
            }
            return handleCommonErrors(holdingIdentityShortHash) {
                mgmResourceClient.getPreAuthTokens(
                    it,
                    ownerX500,
                    tokenId,
                    viewInactive
                )
            }.map { it.fromAvro() }
        }

        override fun revokePreAuthToken(
            holdingIdentityShortHash: String,
            preAuthTokenId: String,
            remarks: String?
        ): PreAuthToken {
            val tokenId = parsePreAuthTokenId(preAuthTokenId)
            return try {
                handleCommonErrors(holdingIdentityShortHash) {
                    mgmResourceClient.revokePreAuthToken(
                        it,
                        tokenId,
                        remarks
                    ).fromAvro()
                }
            } catch (e: MembershipPersistenceException) {
                throw ResourceNotFoundException("${e.message}")
            }
        }

        override fun addGroupApprovalRule(
            holdingIdentityShortHash: String,
            ruleInfo: ApprovalRuleRequestParams
        ) = addGroupApprovalRule(holdingIdentityShortHash, ruleInfo, STANDARD)

        override fun addPreAuthGroupApprovalRule(
            holdingIdentityShortHash: String,
            ruleInfo: ApprovalRuleRequestParams
        ) = addGroupApprovalRule(holdingIdentityShortHash, ruleInfo, PREAUTH)

        private fun addGroupApprovalRule(
            holdingIdentityShortHash: String,
            ruleInfo: ApprovalRuleRequestParams,
            ruleType: ApprovalRuleType
        ): ApprovalRuleInfo {
            validateRegex(ruleInfo.ruleRegex)
            return try {
                handleCommonErrors(holdingIdentityShortHash) {
                    mgmResourceClient.addApprovalRule(
                        it,
                        ApprovalRuleParams(ruleInfo.ruleRegex, ruleType, ruleInfo.ruleLabel)
                    )
                }.toHttpType()
            } catch (e: MembershipPersistenceException) {
                throw BadRequestException("${e.message}")
            }
        }

        override fun getGroupApprovalRules(
            holdingIdentityShortHash: String
        ) = getGroupApprovalRules(holdingIdentityShortHash, STANDARD)

        override fun getPreAuthGroupApprovalRules(
            holdingIdentityShortHash: String
        ) = getGroupApprovalRules(holdingIdentityShortHash, PREAUTH)

        private fun getGroupApprovalRules(
            holdingIdentityShortHash: String,
            ruleType: ApprovalRuleType
        ) = handleCommonErrors(holdingIdentityShortHash) {
            mgmResourceClient.getApprovalRules(it, ruleType)
        }.map { it.toHttpType() }

        override fun deleteGroupApprovalRule(
            holdingIdentityShortHash: String,
            ruleId: String
        ) = deleteGroupApprovalRule(holdingIdentityShortHash, ruleId, STANDARD)

        override fun deletePreAuthGroupApprovalRule(
            holdingIdentityShortHash: String,
            ruleId: String
        ) = deleteGroupApprovalRule(holdingIdentityShortHash, ruleId, PREAUTH)

        override fun viewRegistrationRequests(
            holdingIdentityShortHash: String,
            requestSubjectX500Name: String?,
            viewHistoric: Boolean,
        ): Collection<RestRegistrationRequestStatus> {
            return try {
                val requestSubject = requestSubjectX500Name?.let {
                    parseX500Name("requestSubjectX500Name", it)
                }
                handleCommonErrors(holdingIdentityShortHash) {
                    mgmResourceClient.viewRegistrationRequests(
                        it,
                        requestSubject,
                        viewHistoric,
                    )
                }.map { it.toRest() }
            } catch (e: ContextDeserializationException) {
                throw InternalServerException("${e.message}")
            }
        }

        override fun approveRegistrationRequest(
            holdingIdentityShortHash: String,
            requestId: String,
        ) {
            val registrationId = parseRegistrationRequestId(requestId)
            try {
                handleCommonErrors(holdingIdentityShortHash) {
                    mgmResourceClient.reviewRegistrationRequest(
                        it, registrationId, true
                    )
                }
            } catch (e: IllegalArgumentException) {
                throw BadRequestException("${e.message}")
            } catch (e: ContextDeserializationException) {
                throw InternalServerException("${e.message}")
            }
        }

        override fun declineRegistrationRequest(
            holdingIdentityShortHash: String,
            requestId: String,
            reason: ManualDeclinationReason
        ) {
            val registrationId = parseRegistrationRequestId(requestId)
            try {
                handleCommonErrors(holdingIdentityShortHash) {
                    mgmResourceClient.reviewRegistrationRequest(
                        it, registrationId, false, reason
                    )
                }
            } catch (e: IllegalArgumentException) {
                throw BadRequestException("${e.message}")
            }
        }

        override fun suspendMember(holdingIdentityShortHash: String, suspensionParams: SuspensionActivationParameters) {
            val memberName = parseX500Name("suspensionParams.x500Name", suspensionParams.x500Name)
            try {
                handleCommonErrors(holdingIdentityShortHash) {
                    mgmResourceClient.suspendMember(
                        it,
                        memberName,
                        suspensionParams.serialNumber,
                        suspensionParams.reason,
                    )
                }
            } catch (e: IllegalArgumentException) {
                throw BadRequestException("${e.message}")
            } catch (e: NoSuchElementException) {
                throw ResourceNotFoundException("${e.message}")
            } catch (e: PessimisticLockException) {
                throw InvalidStateChangeException("${e.message}")
            } catch (e: InvalidEntityUpdateException) {
                throw InvalidStateChangeException("${e.message}")
            }
        }
        override fun activateMember(holdingIdentityShortHash: String, activationParams: SuspensionActivationParameters) {
            val memberName = parseX500Name("activationParams.x500Name", activationParams.x500Name)
            try {
                handleCommonErrors(holdingIdentityShortHash) {
                    mgmResourceClient.activateMember(
                        it,
                        memberName,
                        activationParams.serialNumber,
                        activationParams.reason,
                    )
                }
            } catch (e: IllegalArgumentException) {
                throw BadRequestException("${e.message}")
            } catch (e: NoSuchElementException) {
                throw ResourceNotFoundException("${e.message}")
            } catch (e: PessimisticLockException) {
                throw InvalidStateChangeException("${e.message}")
            } catch (e: InvalidEntityUpdateException) {
                throw InvalidStateChangeException("${e.message}")
            }
        }

        override fun updateGroupParameters(
            holdingIdentityShortHash: String,
            newGroupParameters: RestGroupParameters,
        ): RestGroupParameters {
            return try {
                handleCommonErrors(holdingIdentityShortHash) {
                    val newParametersMap = newGroupParameters.parameters
                    validateGroupParametersUpdate(newParametersMap)
                    RestGroupParameters(mgmResourceClient.updateGroupParameters(it, newParametersMap).toMap())
                }
            } catch (e: PessimisticLockException) {
                throw InvalidStateChangeException("${e.message}")
            }
        }

        private fun validateGroupParametersUpdate(parameters: Map<String, String>) {
            val verifierResult = GroupParametersUpdateVerifier().verify(parameters)
            if (verifierResult is GroupParametersUpdateVerifier.Result.Failure) {
                with(verifierResult.reason) {
                    throw BadRequestException(this)
                }
            }
        }

        private fun RegistrationRequestDetails.toRest() =
            RestRegistrationRequestStatus(
                registrationId,
                registrationSent,
                registrationLastModified,
                registrationStatus.fromAvro(),
                MemberInfoSubmitted(memberProvidedContext.data.array().deserializeContext(deserializer)),
                reason,
                serial,
            )

        private fun net.corda.data.membership.common.v2.RegistrationStatus.fromAvro() = when (this) {
            net.corda.data.membership.common.v2.RegistrationStatus.NEW -> RegistrationStatus.NEW
            net.corda.data.membership.common.v2.RegistrationStatus.SENT_TO_MGM -> RegistrationStatus.SENT_TO_MGM
            net.corda.data.membership.common.v2.RegistrationStatus.RECEIVED_BY_MGM -> RegistrationStatus.RECEIVED_BY_MGM
            net.corda.data.membership.common.v2.RegistrationStatus.STARTED_PROCESSING_BY_MGM ->
                RegistrationStatus.STARTED_PROCESSING_BY_MGM
            net.corda.data.membership.common.v2.RegistrationStatus.PENDING_MEMBER_VERIFICATION ->
                RegistrationStatus.PENDING_MEMBER_VERIFICATION
            net.corda.data.membership.common.v2.RegistrationStatus.PENDING_MANUAL_APPROVAL -> RegistrationStatus.PENDING_MANUAL_APPROVAL
            net.corda.data.membership.common.v2.RegistrationStatus.PENDING_AUTO_APPROVAL -> RegistrationStatus.PENDING_AUTO_APPROVAL
            net.corda.data.membership.common.v2.RegistrationStatus.DECLINED -> RegistrationStatus.DECLINED
            net.corda.data.membership.common.v2.RegistrationStatus.INVALID -> RegistrationStatus.INVALID
            net.corda.data.membership.common.v2.RegistrationStatus.FAILED -> RegistrationStatus.FAILED
            net.corda.data.membership.common.v2.RegistrationStatus.APPROVED -> RegistrationStatus.APPROVED
        }

        private fun deleteGroupApprovalRule(
            holdingIdentityShortHash: String,
            ruleId: String,
            ruleType: ApprovalRuleType
        ) = try {
            handleCommonErrors(holdingIdentityShortHash) {
                mgmResourceClient.deleteApprovalRule(it, ruleId, ruleType)
            }
        } catch (e: MembershipPersistenceException) {
            throw ResourceNotFoundException("${e.message}")
        }

        private fun notAnMgmError(holdingIdentityShortHash: String): Nothing =
            throw InvalidInputDataException(
                details = mapOf("holdingIdentityShortHash" to holdingIdentityShortHash),
                message = "Member with holding identity $holdingIdentityShortHash is not an MGM.",
            )

        private fun parsePreAuthTokenId(preAuthTokenId: String): UUID {
            return try {
                UUID.fromString(preAuthTokenId)
            } catch (e: IllegalArgumentException) {
                throw InvalidInputDataException(
                    details = mapOf("preAuthTokenId" to preAuthTokenId),
                    message = "tokenId is not a valid pre auth token."
                )
            }
        }

        private fun parseX500Name(keyName: String, x500Name: String): MemberX500Name {
            return try {
                MemberX500Name.parse(x500Name)
            } catch (e: IllegalArgumentException) {
                throw InvalidInputDataException(
                    details = mapOf(keyName to x500Name),
                    message = "$keyName is not a valid X500 name: ${e.message}",
                )
            }
        }

        private fun AvroPreAuthToken.fromAvro(): PreAuthToken = PreAuthToken(
            this.id,
            this.ownerX500Name,
            this.ttl, status.fromAvro(),
            this.creationRemark,
            this.removalRemark
        )

        private fun AvroPreAuthTokenStatus.fromAvro(): PreAuthTokenStatus = when (this) {
            AvroPreAuthTokenStatus.AVAILABLE -> PreAuthTokenStatus.AVAILABLE
            AvroPreAuthTokenStatus.REVOKED -> PreAuthTokenStatus.REVOKED
            AvroPreAuthTokenStatus.CONSUMED -> PreAuthTokenStatus.CONSUMED
            AvroPreAuthTokenStatus.AUTO_INVALIDATED -> PreAuthTokenStatus.AUTO_INVALIDATED
        }

        private fun validateRegex(expression: String) {
            if (expression.isBlank()) {
                throw BadRequestException("The regular expression was a blank string.")
            }

            try {
                expression.toRegex()
            } catch (e: PatternSyntaxException) {
                throw BadRequestException("The regular expression's syntax is invalid.\n${e.message}")
            }
        }

        private fun ApprovalRuleDetails.toHttpType() = ApprovalRuleInfo(ruleId, ruleRegex, ruleLabel)

        private fun verifyMutualTlsIsRunning() {
            if (TlsType.getClusterType(configurationGetService::getSmartConfig) != TlsType.MUTUAL) {
                throw BadRequestException(
                    message = "This cluster is configure to use one way TLS. Mutual TLS APIs can not be called.",
                )
            }
        }

        /**
         * Invoke a function with handling for common exceptions across all endpoints.
         */
        private fun <T> handleCommonErrors(
            holdingIdentityShortHash: String,
            func: (ShortHash) -> T
        ): T {
            return try {
                func.invoke(ShortHash.parseOrThrow(holdingIdentityShortHash))
            } catch (e: CouldNotFindEntityException) {
                throw ResourceNotFoundException(e.entity, holdingIdentityShortHash)
            } catch (e: MemberNotAnMgmException) {
                notAnMgmError(holdingIdentityShortHash)
            } catch (e: CordaRPCAPIPartitionException) {
                throw ServiceUnavailableException("Could not perform operation for $holdingIdentityShortHash: Repartition Event!")
            }
        }
    }
}