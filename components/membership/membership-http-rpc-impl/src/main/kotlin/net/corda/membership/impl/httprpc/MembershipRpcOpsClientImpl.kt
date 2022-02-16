package net.corda.membership.impl.httprpc

import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.membership.rpc.request.MembershipRpcRequest
import net.corda.data.membership.rpc.request.MembershipRpcRequestContext
import net.corda.data.membership.rpc.request.RegistrationAction
import net.corda.data.membership.rpc.request.RegistrationRequest
import net.corda.data.membership.rpc.response.RegistrationResponse
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.membership.httprpc.MembershipRpcOpsClient
import net.corda.membership.httprpc.types.MemberInfoSubmitted
import net.corda.membership.httprpc.types.MemberRegistrationRequest
import net.corda.membership.httprpc.types.RegistrationRequestProgress
import net.corda.membership.impl.httprpc.lifecycle.MembershipRpcOpsClientLifecycleHandler
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import java.time.Instant
import java.util.UUID

class MembershipRpcOpsClientImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PublisherFactory::class)
    val publisherFactory: PublisherFactory,
    @Reference(service = ConfigurationReadService::class)
    val configurationReadService: ConfigurationReadService
) : MembershipRpcOpsClient {
    companion object {
        private val logger: Logger = contextLogger()
    }

    private val lifecycleHandler = MembershipRpcOpsClientLifecycleHandler(this)

    private val coordinator = coordinatorFactory.createCoordinator<MembershipRpcOpsClientLifecycleHandler>(lifecycleHandler)

    private val className = this::class.java.simpleName

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
        val request = MembershipRpcRequest(
            MembershipRpcRequestContext(
                UUID.randomUUID().toString(),
                Instant.now()
            ),
            RegistrationRequest(
                memberRegistrationRequest.virtualNodeId,
                RegistrationAction.REQUEST_JOIN
            )
        )

        return registrationResponse(request.sendRequest())
    }

    override fun checkRegistrationProgress(virtualNodeId: String): RegistrationRequestProgress {
        serviceIsRunning()
        val request = MembershipRpcRequest(
            MembershipRpcRequestContext(
                UUID.randomUUID().toString(),
                Instant.now()
            ),
            virtualNodeId
        )

        return registrationResponse(request.sendRequest())
    }

    @Suppress("SpreadOperator")
    private fun registrationResponse(response: RegistrationResponse): RegistrationRequestProgress =
        RegistrationRequestProgress(
            response.registrationSent,
            response.registrationStatus.toString(),
            MemberInfoSubmitted(
                mapOf(
                    "registrationProtocolVersion" to response.registrationProtocolVersion.toString(),
                    *response.memberProvidedContext.items.map { it.key to it.value }.toTypedArray(),
                    *response.additionalInfo.items.map { it.key to it.value }.toTypedArray()
                )
            )
        )

    @Suppress("UNCHECKED_CAST")
    private fun <RESPONSE> MembershipRpcRequest.sendRequest(): RESPONSE {
        return try {
            logger.info("Sending request: $this")
            lifecycleHandler.rpcSender.sendRequest(this).getOrThrow().response as RESPONSE
        } catch (e: Exception) {
            throw CordaRuntimeException(
                "Failed to send request and receive response for membership RPC operation. " + e.message, e
            )
        }
    }

    private fun serviceIsRunning() {
        if(!this.isRunning) {
            throw CordaRuntimeException("$className is not running.")
        }
    }
}