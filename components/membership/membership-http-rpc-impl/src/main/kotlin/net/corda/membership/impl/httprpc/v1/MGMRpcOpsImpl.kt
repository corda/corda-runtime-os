package net.corda.membership.impl.httprpc.v1

import net.corda.httprpc.PluggableRPCOps
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
import net.corda.membership.impl.httprpc.v1.lifecycle.RpcOpsLifecycleHandler
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.read.rpc.extensions.parseOrThrow
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger

@Component(service = [PluggableRPCOps::class])
class MGMRpcOpsImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = MGMOpsClient::class)
    private val mgmOpsClient: MGMOpsClient,
) : MGMRpcOps, PluggableRPCOps<MGMRpcOps>, Lifecycle {
    companion object {
        private val logger: Logger = contextLogger()
    }

    private interface InnerMGMRpcOps {
        fun generateGroupPolicy(holdingIdentityShortHash: String): String

        fun allowClientCertificate(holdingIdentityShortHash: String, clientCertificateSubject: String)

        fun disallowClientCertificate(holdingIdentityShortHash: String, clientCertificateSubject: String)

        fun listClientCertificate(holdingIdentityShortHash: String): List<String>
    }

    override val protocolVersion = 1

    private var impl: InnerMGMRpcOps = InactiveImpl

    private val coordinatorName = LifecycleCoordinatorName.forComponent<MGMRpcOps>(
        protocolVersion.toString()
    )

    private val lifecycleHandler = RpcOpsLifecycleHandler(
        ::activate,
        ::deactivate,
        setOf(LifecycleCoordinatorName.forComponent<MGMOpsClient>())
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

    override fun allowClientCertificate(holdingIdentityShortHash: String, clientCertificateSubject: String) =
        impl.allowClientCertificate(holdingIdentityShortHash, clientCertificateSubject)

    override fun disallowClientCertificate(holdingIdentityShortHash: String, clientCertificateSubject: String) =
        impl.disallowClientCertificate(holdingIdentityShortHash, clientCertificateSubject)

    override fun listClientCertificate(holdingIdentityShortHash: String): List<String> =
        impl.listClientCertificate(holdingIdentityShortHash)

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

        override fun allowClientCertificate(holdingIdentityShortHash: String, clientCertificateSubject: String) {
            throw ServiceUnavailableException(
                "${MGMRpcOpsImpl::class.java.simpleName} is not running. Operation cannot be fulfilled."
            )
        }

        override fun disallowClientCertificate(holdingIdentityShortHash: String, clientCertificateSubject: String) {
            throw ServiceUnavailableException(
                "${MGMRpcOpsImpl::class.java.simpleName} is not running. Operation cannot be fulfilled."
            )
        }

        override fun listClientCertificate(holdingIdentityShortHash: String): List<String> {
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

        override fun allowClientCertificate(holdingIdentityShortHash: String, clientCertificateSubject: String) {
            return try {
                mgmOpsClient.allowClientCertificate(ShortHash.parseOrThrow(holdingIdentityShortHash), clientCertificateSubject)
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

        override fun disallowClientCertificate(holdingIdentityShortHash: String, clientCertificateSubject: String) {
            return try {
                mgmOpsClient.disallowClientCertificate(ShortHash.parseOrThrow(holdingIdentityShortHash), clientCertificateSubject)
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

        override fun listClientCertificate(holdingIdentityShortHash: String): List<String> {
            return try {
                mgmOpsClient.listClientCertificate(ShortHash.parseOrThrow(holdingIdentityShortHash))
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
    }
}