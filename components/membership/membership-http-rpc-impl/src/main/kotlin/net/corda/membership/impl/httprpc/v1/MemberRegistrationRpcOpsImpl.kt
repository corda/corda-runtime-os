package net.corda.membership.impl.httprpc.v1

import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.exception.ServiceUnavailableException
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.membership.client.MemberOpsClient
import net.corda.membership.httprpc.v1.MemberRegistrationRpcOps
import net.corda.membership.httprpc.v1.types.request.MemberRegistrationRequest
import net.corda.membership.httprpc.v1.types.response.RegistrationRequestProgress
import net.corda.membership.impl.httprpc.v1.lifecycle.RpcOpsLifecycleHandler
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger

@Component(service = [PluggableRPCOps::class])
class MemberRegistrationRpcOpsImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = MemberOpsClient::class)
    private val memberOpsClient: MemberOpsClient
) : MemberRegistrationRpcOps, PluggableRPCOps<MemberRegistrationRpcOps>, Lifecycle {
    companion object {
        private val logger: Logger = contextLogger()
    }

    private interface InnerMemberRegistrationRpcOps {
        fun startRegistration(
            holdingIdentityId: String,
            memberRegistrationRequest: MemberRegistrationRequest,
        ): RegistrationRequestProgress

        fun checkRegistrationProgress(holdingIdentityId: String): RegistrationRequestProgress
    }

    private val className = this::class.java.simpleName

    override val protocolVersion = 1

    private var impl: InnerMemberRegistrationRpcOps = InactiveImpl

    private val coordinatorName = LifecycleCoordinatorName.forComponent<MemberRegistrationRpcOps>(
        protocolVersion.toString()
    )

    private val lifecycleHandler = RpcOpsLifecycleHandler(
        ::activate,
        ::deactivate,
        setOf(LifecycleCoordinatorName.forComponent<MemberOpsClient>())
    )

    private val coordinator = coordinatorFactory.createCoordinator(coordinatorName, lifecycleHandler)

    override val targetInterface: Class<MemberRegistrationRpcOps> = MemberRegistrationRpcOps::class.java

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        logger.info("$className started.")
        coordinator.start()
    }

    override fun stop() {
        logger.info("$className stopped.")
        coordinator.stop()
    }

    override fun startRegistration(
        holdingIdentityId: String,
        memberRegistrationRequest: MemberRegistrationRequest
    ) = impl.startRegistration(holdingIdentityId, memberRegistrationRequest)


    override fun checkRegistrationProgress(holdingIdentityId: String) =
        impl.checkRegistrationProgress(holdingIdentityId)

    fun activate(reason: String) {
        impl = ActiveImpl()
        coordinator.updateStatus(LifecycleStatus.UP, reason)
    }

    fun deactivate(reason: String) {
        coordinator.updateStatus(LifecycleStatus.DOWN, reason)
        impl = InactiveImpl
    }

    private object InactiveImpl : InnerMemberRegistrationRpcOps {
        override fun startRegistration(
            holdingIdentityId: String,
            memberRegistrationRequest: MemberRegistrationRequest,
        ) =
            throw ServiceUnavailableException(
                "${MemberRegistrationRpcOpsImpl::class.java.simpleName} is not running. Operation cannot be fulfilled."
            )

        override fun checkRegistrationProgress(holdingIdentityId: String) =
            throw ServiceUnavailableException(
                "${MemberRegistrationRpcOpsImpl::class.java.simpleName} is not running. Operation cannot be fulfilled."
            )
    }

    private inner class ActiveImpl : InnerMemberRegistrationRpcOps {
        override fun startRegistration(
            holdingIdentityId: String,
            memberRegistrationRequest: MemberRegistrationRequest,
        ): RegistrationRequestProgress {
            return memberOpsClient.startRegistration(memberRegistrationRequest.toDto(holdingIdentityId)).fromDto()
        }

        override fun checkRegistrationProgress(holdingIdentityId: String): RegistrationRequestProgress {
            return memberOpsClient.checkRegistrationProgress(holdingIdentityId).fromDto()
        }
    }
}