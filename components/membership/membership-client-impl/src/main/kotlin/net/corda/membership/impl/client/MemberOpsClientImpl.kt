package net.corda.membership.impl.client

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.membership.rpc.request.MembershipRpcRequest
import net.corda.data.membership.rpc.request.MembershipRpcRequestContext
import net.corda.data.membership.rpc.request.RegistrationAction
import net.corda.data.membership.rpc.request.RegistrationRequest
import net.corda.data.membership.rpc.request.RegistrationStatusRequest
import net.corda.data.membership.rpc.response.MembershipRpcResponse
import net.corda.data.membership.rpc.response.RegistrationResponse
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.membership.client.MemberOpsClient
import net.corda.membership.client.dto.MemberInfoSubmittedDto
import net.corda.membership.client.dto.MemberRegistrationRequestDto
import net.corda.membership.client.dto.RegistrationRequestProgressDto
import net.corda.messaging.api.config.toMessagingConfig
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import java.time.Instant
import java.util.UUID

@Component(service = [MemberOpsClient::class])
class MemberOpsClientImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PublisherFactory::class)
    val publisherFactory: PublisherFactory,
    @Reference(service = ConfigurationReadService::class)
    val configurationReadService: ConfigurationReadService
) : MemberOpsClient {
    companion object {
        private val logger: Logger = contextLogger()

        const val CLIENT_ID = "membership.ops.rpc"
        const val GROUP_NAME = "membership.ops.rpc"
    }

    private interface InnerMemberOpsClient : AutoCloseable {
        fun startRegistration(memberRegistrationRequest: MemberRegistrationRequestDto): RegistrationRequestProgressDto

        fun checkRegistrationProgress(holdingIdentityId: String): RegistrationRequestProgressDto
    }

    private var impl: InnerMemberOpsClient = InactiveImpl()

    // for watching the config changes
    private var configHandle: AutoCloseable? = null

    // for checking the components' health
    private var componentHandle: AutoCloseable? = null

    private val coordinator = coordinatorFactory.createCoordinator<MemberOpsClient>(::processEvent)

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

    private fun updateStatus(status: LifecycleStatus, reason: String) {
        if (coordinator.status != status) {
            coordinator.updateStatus(status, reason)
        }
    }

    override fun startRegistration(memberRegistrationRequest: MemberRegistrationRequestDto) =
        impl.startRegistration(memberRegistrationRequest)

    override fun checkRegistrationProgress(holdingIdentityId: String) =
        impl.checkRegistrationProgress(holdingIdentityId)

    private fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                componentHandle?.close()
                componentHandle = coordinator.followStatusChangesByName(
                    setOf(
                        LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
                    )
                )
            }
            is StopEvent -> {
                componentHandle?.close()
                configHandle?.close()
                deactivate("Handling the stop event for component.")
            }
            is RegistrationStatusChangeEvent -> {
                when (event.status) {
                    LifecycleStatus.UP -> {
                        configHandle?.close()
                        configHandle = configurationReadService.registerComponentForUpdates(
                            coordinator,
                            setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG)
                        )
                    }
                    else -> {
                        configHandle?.close()
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
                            responseType = MembershipRpcResponse::class.java
                        ),
                        event.config.toMessagingConfig()
                    ).also {
                        it.start()
                    }
                )
                updateStatus(LifecycleStatus.UP, "Dependencies are UP and configuration received.")
            }
        }
    }

    private fun deactivate(reason: String) {
        updateStatus(LifecycleStatus.DOWN, reason)
        val current = impl
        impl = InactiveImpl()
        current.close()
    }

    private class InactiveImpl : InnerMemberOpsClient {
        companion object {
            const val ERROR_MSG = "Service is in an incorrect state for calling."
        }

        override fun startRegistration(memberRegistrationRequest: MemberRegistrationRequestDto) =
            throw IllegalStateException(ERROR_MSG)

        override fun checkRegistrationProgress(holdingIdentityId: String) =
            throw IllegalStateException(ERROR_MSG)

        override fun close() = Unit

    }

    private class ActiveImpl(
        val rpcSender: RPCSender<MembershipRpcRequest, MembershipRpcResponse>
    ) : InnerMemberOpsClient {
        override fun startRegistration(memberRegistrationRequest: MemberRegistrationRequestDto): RegistrationRequestProgressDto {
            val request = MembershipRpcRequest(
                MembershipRpcRequestContext(
                    UUID.randomUUID().toString(),
                    Instant.now()
                ),
                RegistrationRequest(
                    memberRegistrationRequest.holdingIdentityId,
                    RegistrationAction.valueOf(memberRegistrationRequest.action.name)
                )
            )

            return registrationResponse(request.sendRequest())
        }

        override fun checkRegistrationProgress(holdingIdentityId: String): RegistrationRequestProgressDto {
            val request = MembershipRpcRequest(
                MembershipRpcRequestContext(
                    UUID.randomUUID().toString(),
                    Instant.now()
                ),
                RegistrationStatusRequest(holdingIdentityId)
            )

            return registrationResponse(request.sendRequest())
        }

        override fun close() = rpcSender.close()

        @Suppress("SpreadOperator")
        private fun registrationResponse(response: RegistrationResponse): RegistrationRequestProgressDto =
            RegistrationRequestProgressDto(
                response.registrationSent,
                response.registrationStatus.toString(),
                MemberInfoSubmittedDto(
                    mapOf(
                        "registrationProtocolVersion" to response.registrationProtocolVersion.toString(),
                        *response.memberProvidedContext.items.map { it.key to it.value }.toTypedArray(),
                        *response.additionalInfo.items.map { it.key to it.value }.toTypedArray()
                    )
                )
            )

        @Suppress("UNCHECKED_CAST")
        private inline fun <reified RESPONSE> MembershipRpcRequest.sendRequest(): RESPONSE {
            try {
                logger.info("Sending request: $this")
                val response = rpcSender.sendRequest(this).getOrThrow()
                require(response != null && response.responseContext != null && response.response != null) {
                    "Response cannot be null."
                }
                require(this.requestContext.requestId == response.responseContext.requestId) {
                    "Request ID must match in the request and response."
                }
                require(this.requestContext.requestTimestamp == response.responseContext.requestTimestamp) {
                    "Request timestamp must match in the request and response."
                }
                require(response.response is RESPONSE) {
                    "Expected ${RESPONSE::class.java} as response type, but received ${response.response.javaClass}."
                }

                return response.response as RESPONSE
            } catch (e: Exception) {
                throw CordaRuntimeException(
                    "Failed to send request and receive response for membership RPC operation. " + e.message, e
                )
            }
        }
    }
}