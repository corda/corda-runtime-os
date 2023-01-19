package net.corda.membership.impl.httprpc.v1

import net.corda.configuration.read.ConfigurationGetService
import net.corda.configuration.read.ConfigurationReadService
import net.corda.httprpc.PluggableRPCOps
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
import net.corda.membership.httprpc.v1.MGMRpcOps
import net.corda.membership.httprpc.v1.types.response.PreAuthToken
import net.corda.membership.impl.httprpc.v1.lifecycle.RpcOpsLifecycleHandler
import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.P2PParameters.TlsType
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.read.rpc.extensions.parseOrThrow
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import java.util.UUID

@Component(service = [PluggableRPCOps::class])
class MGMRpcOpsImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = MGMOpsClient::class)
    private val mgmOpsClient: MGMOpsClient,
    @Reference(service = ConfigurationGetService::class)
    private val configurationGetService: ConfigurationGetService,
) : MGMRpcOps, PluggableRPCOps<MGMRpcOps>, Lifecycle {
    companion object {
        private val logger: Logger = contextLogger()
    }

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
        fun generatePreAuthToken(holdingIdentityShortHash: String, ownerX500Name: String, ttl: Int, remarks: String?): PreAuthToken
        fun getPreAuthTokens(
            holdingIdentityShortHash: String,
            ownerX500Name: String?,
            preAuthTokenId: String?,
            viewInactive: Boolean
        ): Collection<PreAuthToken>
        fun revokePreAuthToken(holdingIdentityShortHash: String, preAuthTokenId: String, remarks: String? = null): PreAuthToken
    }

    override val protocolVersion = 1

    private var impl: InnerMGMRpcOps = InactiveImpl

    private val coordinatorName = LifecycleCoordinatorName.forComponent<MGMRpcOps>(
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

    override val targetInterface: Class<MGMRpcOps> = MGMRpcOps::class.java

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

    override fun generatePreAuthToken(holdingIdentityShortHash: String, ownerX500Name: String, ttl: Int, remarks: String?) =
        impl.generatePreAuthToken(holdingIdentityShortHash, ownerX500Name, ttl, remarks)

    override fun getPreAuthTokens(
        holdingIdentityShortHash: String,
        ownerX500Name: String?,
        preAuthTokenId: String?,
        viewInactive: Boolean
    ) = impl.getPreAuthTokens(holdingIdentityShortHash, ownerX500Name, preAuthTokenId, viewInactive)

    override fun revokePreAuthToken(holdingIdentityShortHash: String, preAuthTokenId: String, remarks: String?) =
        impl.revokePreAuthToken(holdingIdentityShortHash, preAuthTokenId, remarks)

    fun activate(reason: String) {
        impl = ActiveImpl()
        coordinator.updateStatus(LifecycleStatus.UP, reason)
    }

    fun deactivate(reason: String) {
        coordinator.updateStatus(LifecycleStatus.DOWN, reason)
        impl = InactiveImpl
    }

    private object InactiveImpl : InnerMGMRpcOps {
        override fun generateGroupPolicy(holdingIdentityShortHash: String) =
            throw ServiceUnavailableException(
                "${MGMRpcOpsImpl::class.java.simpleName} is not running. Operation cannot be fulfilled."
            )

        override fun mutualTlsAllowClientCertificate(
            holdingIdentityShortHash: String,
            subject: String,
        ) {
            throw ServiceUnavailableException(
                "${MGMRpcOpsImpl::class.java.simpleName} is not running. Operation cannot be fulfilled."
            )
        }

        override fun mutualTlsDisallowClientCertificate(
            holdingIdentityShortHash: String,
            subject: String,
        ) {
            throw ServiceUnavailableException(
                "${MGMRpcOpsImpl::class.java.simpleName} is not running. Operation cannot be fulfilled."
            )
        }

        override fun mutualTlsListClientCertificate(holdingIdentityShortHash: String): Collection<String> {
            throw ServiceUnavailableException(
                "${MGMRpcOpsImpl::class.java.simpleName} is not running. Operation cannot be fulfilled."
            )
        }

        override fun generatePreAuthToken(
            holdingIdentityShortHash: String,
            ownerX500Name: String,
            ttl: Int,
            remarks: String?
        ): PreAuthToken {
            throw ServiceUnavailableException(
                "${MGMRpcOpsImpl::class.java.simpleName} is not running. Operation cannot be fulfilled."
            )
        }

        override fun getPreAuthTokens(
            holdingIdentityShortHash: String,
            ownerX500Name: String?,
            preAuthTokenId: String?,
            viewInactive: Boolean
        ): Collection<PreAuthToken> {
            throw ServiceUnavailableException(
                "${MGMRpcOpsImpl::class.java.simpleName} is not running. Operation cannot be fulfilled."
            )
        }

        override fun revokePreAuthToken(holdingIdentityShortHash: String, preAuthTokenId: String, remarks: String?): PreAuthToken {
            throw ServiceUnavailableException(
                "${MGMRpcOpsImpl::class.java.simpleName} is not running. Operation cannot be fulfilled."
            )
        }
    }

    private inner class ActiveImpl : InnerMGMRpcOps {
        override fun generateGroupPolicy(holdingIdentityShortHash: String): String {
            return try {
                mgmOpsClient.generateGroupPolicy(ShortHash.parseOrThrow(holdingIdentityShortHash))
            } catch (e: CouldNotFindMemberException) {
                throw ResourceNotFoundException("Could not find member with holding identity $holdingIdentityShortHash.")
            } catch (e: MemberNotAnMgmException) {
                throw InvalidInputDataException(
                    details = mapOf(
                        "holdingIdentityShortHash" to holdingIdentityShortHash
                    ),
                    message = "Member with holding identity $holdingIdentityShortHash is not an MGM.",
                )
            }
        }

        private fun verifyMutualTlsIsRunning() {
            if(TlsType.getClusterType(configurationGetService::getSmartConfig) !=  TlsType.MUTUAL) {
                throw BadRequestException(
                    message = "This cluster is configure to use one way TLS. Mutual TLS APIs can not be called.",
                )
            }
        }

        override fun mutualTlsAllowClientCertificate(
            holdingIdentityShortHash: String,
            subject: String
        ) {
            verifyMutualTlsIsRunning()
            val subjectName = try {
                MemberX500Name.parse(subject)
            } catch (e: IllegalArgumentException) {
                throw InvalidInputDataException(
                    details = mapOf(
                        "subject" to subject
                    ),
                    message = "Subject is not a valid X500 name: ${e.message}",
                )
            }
            try {
                mgmOpsClient.mutualTlsAllowClientCertificate(
                    ShortHash.parseOrThrow(holdingIdentityShortHash),
                    subjectName
                )
            } catch (e: MemberNotAnMgmException) {
                throw InvalidInputDataException(
                    details = mapOf(
                        "holdingIdentityShortHash" to holdingIdentityShortHash
                    ),
                    message = "Member with holding identity $holdingIdentityShortHash is not an MGM.",
                )
            }
        }

        override fun mutualTlsDisallowClientCertificate(holdingIdentityShortHash: String, subject: String) {
            verifyMutualTlsIsRunning()
            val subjectName = try {
                MemberX500Name.parse(subject)
            } catch (e: IllegalArgumentException) {
                throw InvalidInputDataException(
                    details = mapOf(
                        "subject" to subject
                    ),
                    message = "Subject is not a valid X500 name: ${e.message}",
                )
            }
            try {
                mgmOpsClient.mutualTlsDisallowClientCertificate(
                    ShortHash.parseOrThrow(holdingIdentityShortHash),
                    subjectName
                )
            } catch (e: MemberNotAnMgmException) {
                throw InvalidInputDataException(
                    details = mapOf(
                        "holdingIdentityShortHash" to holdingIdentityShortHash
                    ),
                    message = "Member with holding identity $holdingIdentityShortHash is not an MGM.",
                )
            }
        }

        override fun mutualTlsListClientCertificate(holdingIdentityShortHash: String): Collection<String> {
            verifyMutualTlsIsRunning()
            return try {
                mgmOpsClient.mutualTlsListClientCertificate(
                    ShortHash.parseOrThrow(holdingIdentityShortHash),
                ).map {
                    it.toString()
                }
            } catch (e: MemberNotAnMgmException) {
                throw InvalidInputDataException(
                    details = mapOf(
                        "holdingIdentityShortHash" to holdingIdentityShortHash
                    ),
                    message = "Member with holding identity $holdingIdentityShortHash is not an MGM.",
                )
            }
        }

        override fun generatePreAuthToken(
            holdingIdentityShortHash: String,
            ownerX500Name: String,
            ttl: Int,
            remarks: String?
        ): PreAuthToken {
            val ownerX500 = try {
                MemberX500Name.parse(ownerX500Name)
            } catch (e: IllegalArgumentException) {
                throw InvalidInputDataException(
                    details = mapOf(
                        "ownerX500Name" to ownerX500Name
                    ),
                    message = "ownerX500Name is not a valid X500 name: ${e.message}",
                )
            }

            return try {
                mgmOpsClient.generatePreAuthToken(
                    ShortHash.parseOrThrow(holdingIdentityShortHash),
                    ownerX500,
                    ttl,
                    remarks
                ).fromDto()
            } catch (e: MemberNotAnMgmException) {
                throw InvalidInputDataException(
                    details = mapOf(
                        "holdingIdentityShortHash" to holdingIdentityShortHash
                    ),
                    message = "Member with holding identity $holdingIdentityShortHash is not an MGM.",
                )
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
                ).map { it.fromDto() }
            } catch (e: MemberNotAnMgmException) {
                throw InvalidInputDataException(
                    details = mapOf(
                        "holdingIdentityShortHash" to holdingIdentityShortHash
                    ),
                    message = "Member with holding identity $holdingIdentityShortHash is not an MGM.",
                )
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
                ).fromDto()
            } catch (e: MemberNotAnMgmException) {
                throw InvalidInputDataException(
                    details = mapOf(
                        "holdingIdentityShortHash" to holdingIdentityShortHash
                    ),
                    message = "Member with holding identity $holdingIdentityShortHash is not an MGM.",
                )
            }
        }
    }
}