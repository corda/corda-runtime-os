package net.corda.membership.impl.rest.v1

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
import net.corda.membership.client.MGMResourceClient
import net.corda.membership.client.MemberNotAnMgmException
import net.corda.membership.httprpc.v1.MGMRestResource
import net.corda.membership.httprpc.v1.types.request.ApprovalRuleRequestParams
import net.corda.membership.httprpc.v1.types.request.PreAuthTokenRequest
import net.corda.membership.httprpc.v1.types.response.ApprovalRuleInfo
import net.corda.membership.httprpc.v1.types.response.PreAuthToken
import net.corda.membership.httprpc.v1.types.response.PreAuthTokenStatus
import net.corda.membership.impl.rest.v1.lifecycle.RestResourceLifecycleHandler
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

@Suppress("TooManyFunctions")
@Component(service = [PluggableRestResource::class])
class MGMRestResourceImpl internal constructor(
    coordinatorFactory: LifecycleCoordinatorFactory,
    private val mgmResourceClient: MGMResourceClient,
    private val configurationGetService: ConfigurationGetService,
    private val clock: Clock = UTCClock(),
) : MGMRestResource, PluggableRestResource<MGMRestResource>, Lifecycle {

    @Activate
    constructor(
        @Reference(service = LifecycleCoordinatorFactory::class)
        coordinatorFactory: LifecycleCoordinatorFactory,
        @Reference(service = MGMResourceClient::class)
        mgmResourceClient: MGMResourceClient,
        @Reference(service = ConfigurationGetService::class)
        configurationGetService: ConfigurationGetService,
    ) : this(
        coordinatorFactory,
        mgmResourceClient,
        configurationGetService,
        UTCClock()
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
    }

    override val protocolVersion = 1

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

    fun activate(reason: String) {
        impl = ActiveImpl()
        coordinator.updateStatus(LifecycleStatus.UP, reason)
    }

    fun deactivate(reason: String) {
        coordinator.updateStatus(LifecycleStatus.DOWN, reason)
        impl = InactiveImpl
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

        private fun holdingIdentityNotFound(holdingIdentityShortHash: String): Nothing =
            throw ResourceNotFoundException("Holding Identity", holdingIdentityShortHash)

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
            } catch (e: CouldNotFindMemberException) {
                holdingIdentityNotFound(holdingIdentityShortHash)
            } catch (e: MemberNotAnMgmException) {
                notAnMgmError(holdingIdentityShortHash)
            }
        }
    }
}