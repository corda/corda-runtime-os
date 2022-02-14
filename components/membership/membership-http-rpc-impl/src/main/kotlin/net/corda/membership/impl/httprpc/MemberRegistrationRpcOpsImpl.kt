package net.corda.membership.impl.httprpc

import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.exception.ServiceUnavailableException
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.membership.httprpc.MemberRegistrationRpcOps
import net.corda.membership.httprpc.MembershipRpcOpsClient
import net.corda.membership.httprpc.types.MemberRegistrationRequest
import net.corda.membership.httprpc.types.RegistrationRequestProgress
import net.corda.membership.impl.httprpc.lifecycle.RegistrationRpcOpsLifecycleHandler
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger

@Component(service = [PluggableRPCOps::class])
class MemberRegistrationRpcOpsImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = MembershipRpcOpsClient::class)
    private val membershipRpcOpsClient: MembershipRpcOpsClient
) : MemberRegistrationRpcOps, PluggableRPCOps<MemberRegistrationRpcOps>, Lifecycle {
    companion object {
        private val logger: Logger = contextLogger()
    }

    private val lifecycleHandler = RegistrationRpcOpsLifecycleHandler()

    private val coordinator = coordinatorFactory.createCoordinator<RegistrationRpcOpsLifecycleHandler>(lifecycleHandler)

    private val className = this::class.java.simpleName

    override val protocolVersion = 1

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

    override fun startRegistration(memberRegistrationRequest: MemberRegistrationRequest): RegistrationRequestProgress {
        serviceIsRunning()
        return membershipRpcOpsClient.startRegistration(memberRegistrationRequest)
    }

    override fun checkRegistrationProgress(virtualNodeId: String): RegistrationRequestProgress {
        serviceIsRunning()
        return membershipRpcOpsClient.checkRegistrationProgress(virtualNodeId)
    }

    private fun serviceIsRunning() {
        if(!this.isRunning) {
            throw ServiceUnavailableException("$className is not running. Operation cannot be fulfilled.")
        }
    }
}