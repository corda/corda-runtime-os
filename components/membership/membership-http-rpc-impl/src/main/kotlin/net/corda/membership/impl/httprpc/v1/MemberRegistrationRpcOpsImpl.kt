package net.corda.membership.impl.httprpc.v1

import net.corda.httprpc.PluggableRPCOps
import net.corda.httprpc.exception.ServiceUnavailableException
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.membership.client.MemberOpsClient
import net.corda.membership.httprpc.v1.MemberRegistrationRpcOps
import net.corda.membership.httprpc.v1.types.request.MemberRegistrationRequest
import net.corda.membership.httprpc.v1.types.response.RegistrationRequestProgress
import net.corda.membership.impl.httprpc.v1.lifecycle.RegistrationRpcOpsLifecycleHandler
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

    private val className = this::class.java.simpleName

    override val protocolVersion = 1

    private val coordinatorName = LifecycleCoordinatorName.forComponent<MemberRegistrationRpcOps>(
        protocolVersion.toString()
    )

    private val lifecycleHandler = RegistrationRpcOpsLifecycleHandler()

    private val coordinator = coordinatorFactory.createCoordinator(coordinatorName, lifecycleHandler)


    override val targetInterface: Class<MemberRegistrationRpcOps> = MemberRegistrationRpcOps::class.java

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        logger.info("$className started.")
        memberOpsClient.start()
        coordinator.start()
    }

    override fun stop() {
        logger.info("$className stopped.")
        memberOpsClient.stop()
        coordinator.stop()
    }

    override fun startRegistration(memberRegistrationRequest: MemberRegistrationRequest): RegistrationRequestProgress {
        serviceIsRunning()
        return memberOpsClient.startRegistration(memberRegistrationRequest.toDto()).fromDto()
    }

    override fun checkRegistrationProgress(virtualNodeId: String): RegistrationRequestProgress {
        serviceIsRunning()
        return memberOpsClient.checkRegistrationProgress(virtualNodeId).fromDto()
    }

    private fun serviceIsRunning() {
        if(!isRunning) {
            throw ServiceUnavailableException("$className is not running. Operation cannot be fulfilled.")
        }
    }
}