package net.corda.membership.impl.httprpc.v1

import net.corda.configuration.read.ConfigurationGetService
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.membership.common.ApprovalRuleDetails
import net.corda.data.membership.common.ApprovalRuleType
import net.corda.data.membership.common.ApprovalRuleType.PREAUTH
import net.corda.data.membership.common.ApprovalRuleType.STANDARD
import net.corda.httprpc.PluggableRestResource
import net.corda.httprpc.exception.BadRequestException
import net.corda.httprpc.exception.InvalidInputDataException
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.httprpc.exception.ServiceUnavailableException
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.membership.client.CouldNotFindMemberException
import net.corda.membership.client.MGMOpsClient
import net.corda.membership.client.MemberNotAnMgmException
import net.corda.membership.httprpc.v1.MGMRestResource
import net.corda.membership.httprpc.v1.types.request.ApprovalRuleRequestParams
import net.corda.membership.httprpc.v1.types.request.PreAuthTokenRequest
import net.corda.membership.httprpc.v1.types.response.ApprovalRuleInfo
import net.corda.membership.httprpc.v1.types.response.PreAuthToken
import net.corda.membership.httprpc.v1.types.response.PreAuthTokenStatus
import net.corda.membership.impl.httprpc.v1.lifecycle.RpcOpsLifecycleHandler
import net.corda.membership.lib.approval.ApprovalRuleParams
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsType
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.read.rpc.extensions.parseOrThrow
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.UUID
import java.util.regex.PatternSyntaxException
import net.corda.data.membership.preauth.PreAuthToken as AvroPreAuthToken
import net.corda.data.membership.preauth.PreAuthTokenStatus as AvroPreAuthTokenStatus

@Component(service = [PluggableRestResource::class])
class MGMRestResourceImpl internal constructor(
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    private val mgmOpsClient: MGMOpsClient,
    private val configurationGetService: ConfigurationGetService,
    private val clock: Clock = UTCClock(),
) : MGMRestResource, PluggableRestResource<MGMRestResource>, Lifecycle {

    @Activate constructor(
        @Reference(service = LifecycleCoordinatorFactory::class)
        coordinatorFactory: LifecycleCoordinatorFactory,
        @Reference(service = MGMOpsClient::class)
        mgmOpsClient: MGMOpsClient,
        @Reference(service = ConfigurationGetService::class)
        configurationGetService: ConfigurationGetService,
    ) : this(
        coordinatorFactory,
        mgmOpsClient,
        configurationGetService,
        UTCClock()
    )

    private interface InnerMGMRpcOps {
        fun generateGroupPolicy(holdingIdentityShortHash: String): String
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
        fun generatePreAuthToken(holdingIdentityShortHash: String, request: PreAuthTokenRequest): PreAuthToken
        fun getPreAuthTokens(
            holdingIdentityShortHash: String,
            ownerX500Name: String?,
            preAuthTokenId: String?,
            viewInactive: Boolean
        ): Collection<PreAuthToken>
        fun revokePreAuthToken(holdingIdentityShortHash: String, preAuthTokenId: String, remarks: String? = null): PreAuthToken

        fuaddGroupApprovalRule(
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
    }

    override val protocolVersion = 1

    private var impl: InnerMGMRpcOps = InactiveImpl

    private val coordinatorName = LifecycleCoordinatorName.forComponent<MGMRestResource>(
        protocolVersion.toString()
    )

    private val lifecycleHandler = RpcOpsLifecycleHandler(
        ::activate,
        ::deactivate,
        setOf(
            LifecycleCoordinatorName.forComponent<MGMOpsClient>(),
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

    fun activate(reason: String) {
        impl = ActiveImpl()
        coordinator.updateStatus(LifecycleStatus.UP, reason)
    }

    fun deactivate(reason: String) {
        coordinator.updateStatus(LifecycleStatus.DOWN, reason)
        impl = InactiveImpl
    }

    private object InactiveImpl : InnerMGMRpcOps {

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
        ): PreAuthToken  = throwNotRunningException()

        override fun getPreAuthTokens(
            holdingIdentityShortHash: String,
            ownerX500Name: String?,
            preAuthTokenId: String?,
            viewInactive: Boolean
        ): Collection<PreAuthToken>  = throwNotRunningException()

        override fun revokePreAuthToken(
            holdingIdentityShortHash: String,
            preAuthTokenId: String,
            remarks: String?
        ): PreAuthToken  = throwNotRunningException()

        private fun <T> throwNotRunningException(): T {
            throw ServiceUnavailableException(NOT_RUNNING_ERROR)
        }
    }

    @Suppress("TooManyFunctions")
    private inner class ActiveImpl : InnerMGMRpcOps {

        override fun generateGroupPolicy(
            holdingIdentityShortHash: String
        ) = executeForCommonErrors(holdingIdentityShortHash) {
            mgmOpsClient.generateGroupPolicy(it)
        }

        override fun mutualTlsAllowClientCertificate(
            holdingIdentityShortHash: String,
            subject: String
        ) {
            verifyMutualTlsIsRunning()
            val subjectName = parseCertificateSubject(subject)
            executeForCommonErrors(holdingIdentityShortHash) {
                mgmOpsClient.mutualTlsAllowClientCertificate(it, subjectName)
            }
        }

        override fun mutualTlsDisallowClientCertificate(holdingIdentityShortHash: String, subject: String) {
            verifyMutualTlsIsRunning()
            val subjectName = parseCertificateSubject(subject)
            executeForCommonErrors(holdingIdentityShortHash) {
                mgmOpsClient.mutualTlsDisallowClientCertificate(it, subjectName)
            }
        }

        override fun mutualTlsListClientCertificate(holdingIdentityShortHash: String): Collection<String> {
            verifyMutualTlsIsRunning()
            return executeForCommonErrors(holdingIdentityShortHash) { shortHash ->
                mgmOpsClient.mutualTlsListClientCertificate(shortHash).map { it.toString() }
            }
        }

        override fun generatePreAuthToken(
            holdingIdentityShortHash: String,
            request: PreAuthTokenRequest
        ): PreAuthToken {
            val ownerX500 = try {
                MemberX500Name.parse(request.ownerX500Name)
            } catch (e: IllegalArgumentException) {
                throw InvalidInputDataException(
                    details = mapOf(
                        "ownerX500Name" to request.ownerX500Name
                    ),
                    message = "ownerX500Name is not a valid X500 name: ${e.message}",
                )
            }

            val ttlAsInstant =  request.ttl?.let{ ttl ->
                clock.instant() + ttl
            }

            return try {
                mgmOpsClient.generatePreAuthToken(
                    ShortHash.parseOrThrow(holdingIdentityShortHash),
                    ownerX500,
                    ttlAsInstant,
                    request.remarks
                ).fromAvro()
            }  catch (e: CouldNotFindMemberException) {
                throw ResourceNotFoundException("Could not find member with holding identity $holdingIdentityShortHash.")
            } catch (e: MemberNotAnMgmException) {
                invalidInput(holdingIdentityShortHash)
            }
        }

        override fun getPreAuthTokens(
            holdingIdentityShortHash: String,
            ownerX500Name: String?,
            preAuthTokenId: String?,
            viewInactive: Boolean
        ): Collection<PreAuthToken> {
            val ownerX500 = ownerX500Name?. let {
                try {
                    MemberX500Name.parse(it)
                } catch (e: IllegalArgumentException) {
                    throw InvalidInputDataException(
                        details = mapOf(
                            "ownerX500Name" to ownerX500Name
                        ),
                        message = "ownerX500Name is not a valid X500 name: ${e.message}",
                    )
                }
            }

            val tokenId = preAuthTokenId?.let { try {
                UUID.fromString(it)
            } catch (e: java.lang.IllegalArgumentException) {
                throw InvalidInputDataException(
                    details = mapOf("preAuthTokenId" to it),
                    message = "tokenId is not a valid pre auth token."
                )
            }
            }
            return try {
                mgmOpsClient.getPreAuthTokens(
                    ShortHash.parseOrThrow(holdingIdentityShortHash),
                    ownerX500,
                    tokenId,
                    viewInactive
                ).map { it.fromAvro() }
            } catch (e: CouldNotFindMemberException) {
                throw ResourceNotFoundException("Could not find member with holding identity $holdingIdentityShortHash.")
            } catch (e: MemberNotAnMgmException) {
                invalidInput(holdingIdentityShortHash)
            }
        }

        override fun revokePreAuthToken(holdingIdentityShortHash: String, preAuthTokenId: String, remarks: String?): PreAuthToken {
            val tokenId =  try {
                UUID.fromString(preAuthTokenId)
            } catch (e: java.lang.IllegalArgumentException) {
                throw InvalidInputDataException(
                    details = mapOf("preAuthTokenId" to preAuthTokenId),
                    message = "tokenId is not a valid pre auth token."
                )
            }

            return try {
                mgmOpsClient.revokePreAuthToken(
                    ShortHash.parseOrThrow(holdingIdentityShortHash),
                    tokenId,
                    remarks
                ).fromAvro()
            } catch (e: CouldNotFindMemberException) {
                throw ResourceNotFoundException("Could not find member with holding identity $holdingIdentityShortHash.")
            } catch (e: MemberNotAnMgmException) {
                invalidInput(holdingIdentityShortHash)
            } catch  (e: MembershipPersistenceException) {
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
            return try {
                executeForCommonErrors(holdingIdentityShortHash) { shortHash ->
                    validateRegex(ruleInfo.ruleRegex)
                    mgmOpsClient.addApprovalRule(
                        shortHash,
                        ApprovalRuleParams(ruleInfo.ruleRegex, ruleType, ruleInfo.ruleLabel)
                    ).toHttpType()
                }
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
        ) = executeForCommonErrors(holdingIdentityShortHash) { shortHash ->
            mgmOpsClient.getApprovalRules(shortHash, ruleType).map { it.toHttpType() }
        }

        override fun deleteGroupApprovalRule(
            holdingIdentityShortHash: String,
            ruleId: String
        ) = deleteGroupApprovalRule(holdingIdentityShortHash, ruleId, STANDARD)

        override fun deletePreAuthGroupApprovalRule(
            holdingIdentityShortHash: String,
            ruleId: String
        ) = deleteGroupApprovalRule(holdingIdentityShortHash, ruleId, PREAUTH)

        private fun deleteGroupApprovalRule(
            holdingIdentityShortHash: String,
            ruleId: String,
            ruleType: ApprovalRuleType
        ) = try {
            executeForCommonErrors(holdingIdentityShortHash) {
                mgmOpsClient.deleteApprovalRule(it, ruleId, ruleType)
            }
        } catch (e: MembershipPersistenceException) {
            throw ResourceNotFoundException("${e.message}")
        }

        private fun holdingIdentityNotFound(holdingIdentityShortHash: String): Nothing =
            throw ResourceNotFoundException("Holding Identity", holdingIdentityShortHash)

        private fun invalidInput(holdingIdentityShortHash: String): Nothing =
            throw InvalidInputDataException(
                details = mapOf("holdingIdentityShortHash" to holdingIdentityShortHash),
                message = "Member with holding identity $holdingIdentityShortHash is not an MGM.",
            )

        private fun AvroPreAuthToken.fromAvro(): PreAuthToken =
            PreAuthToken(this.id, this.ownerX500Name, this.ttl, status.fromAvro(), this.creationRemark, this.removalRemark)

        private fun AvroPreAuthTokenStatus.fromAvro(): PreAuthTokenStatus = when(this) {
            AvroPreAuthTokenStatus.AVAILABLE -> PreAuthTokenStatus.AVAILABLE
            AvroPreAuthTokenStatus.REVOKED -> PreAuthTokenStatus.REVOKED
            AvroPreAuthTokenStatus.CONSUMED -> PreAuthTokenStatus.CONSUMED
            AvroPreAuthTokenStatus.AUTO_INVALIDATED -> PreAuthTokenStatus.AUTO_INVALIDATED
        }

        private fun validateRegex(expression: String) {
            expression.toRegex()
        }

        private fun ApprovalRuleDetails.toHttpType() = ApprovalRuleInfo(ruleId, ruleRegex, ruleLabel)

        private fun verifyMutualTlsIsRunning() {
            if (TlsType.getClusterType(configurationGetService::getSmartConfig) != TlsType.MUTUAL) {
                throw BadRequestException(
                    message = "This cluster is configure to use one way TLS. Mutual TLS APIs can not be called.",
                )
            }
        }

        private fun parseCertificateSubject(subject: String): MemberX500Name {
            return try {
                MemberX500Name.parse(subject)
            } catch (e: IllegalArgumentException) {
                throw InvalidInputDataException(
                    details = mapOf("subject" to subject),
                    message = "Subject is not a valid X500 name: ${e.message}",
                )
            }
        }

        private fun <T> executeForCommonErrors(
            holdingIdentityShortHash: String,
            func: (ShortHash) -> T
        ): T {
            return try {
                func.invoke(ShortHash.parseOrThrow(holdingIdentityShortHash))
            } catch (e: CouldNotFindMemberException) {
                holdingIdentityNotFound(holdingIdentityShortHash)
            } catch (e: MemberNotAnMgmException) {
                invalidInput(holdingIdentityShortHash)
            } catch (e: PatternSyntaxException) {
                throw BadRequestException("The regular expression's syntax is invalid.")
            }
        }
    }
}