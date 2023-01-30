package net.corda.membership.impl.client

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.common.RegistrationStatusDetails
import net.corda.data.membership.rpc.request.MembershipRpcRequest
import net.corda.data.membership.rpc.request.MembershipRpcRequestContext
import net.corda.data.membership.rpc.request.RegistrationRpcAction
import net.corda.data.membership.rpc.request.RegistrationRpcRequest
import net.corda.data.membership.rpc.request.RegistrationStatusRpcRequest
import net.corda.data.membership.rpc.request.RegistrationStatusSpecificRpcRequest
import net.corda.data.membership.rpc.response.MembershipRpcResponse
import net.corda.data.membership.rpc.response.RegistrationRpcResponse
import net.corda.data.membership.rpc.response.RegistrationRpcStatus
import net.corda.data.membership.rpc.response.RegistrationStatusResponse
import net.corda.data.membership.rpc.response.RegistrationsStatusResponse
import net.corda.libs.configuration.helper.getConfig
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
import net.corda.membership.client.RegistrationProgressNotFoundException
import net.corda.membership.client.dto.MemberInfoSubmittedDto
import net.corda.membership.client.dto.MemberRegistrationRequestDto
import net.corda.membership.client.dto.RegistrationRequestProgressDto
import net.corda.membership.client.dto.RegistrationRequestStatusDto
import net.corda.membership.client.dto.RegistrationStatusDto
import net.corda.membership.lib.toWire
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.utilities.concurrent.getOrThrow
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.seconds
import net.corda.virtualnode.ShortHash
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.TimeoutException

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
        private val logger: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val ERROR_MSG = "Service is in an incorrect state for calling."

        const val CLIENT_ID = "membership.ops.rpc"
        const val GROUP_NAME = "membership.ops.rpc"

        private val clock = UTCClock()

        private val TIMEOUT = 20.seconds
    }

    private interface InnerMemberOpsClient : AutoCloseable {
        fun startRegistration(memberRegistrationRequest: MemberRegistrationRequestDto): RegistrationRequestProgressDto

        fun checkRegistrationProgress(holdingIdentityShortHash: ShortHash): List<RegistrationRequestStatusDto>

        fun checkSpecificRegistrationProgress(
            holdingIdentityShortHash: ShortHash,
            registrationRequestId: String
        ): RegistrationRequestStatusDto?
    }

    private var impl: InnerMemberOpsClient = InactiveImpl

    // for watching the config changes
    private var configHandle: AutoCloseable? = null

    // for checking the components' health
    private var componentHandle: AutoCloseable? = null

    private val coordinator = coordinatorFactory.createCoordinator<MemberOpsClient>(::processEvent)

    private val className = this::class.java.simpleName

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }

    override fun startRegistration(memberRegistrationRequest: MemberRegistrationRequestDto) =
        impl.startRegistration(memberRegistrationRequest)

    override fun checkSpecificRegistrationProgress(
        holdingIdentityShortHash: ShortHash,
        registrationRequestId: String
    ) = impl.checkSpecificRegistrationProgress(
        holdingIdentityShortHash, registrationRequestId
    )

    override fun checkRegistrationProgress(holdingIdentityShortHash: ShortHash) =
        impl.checkRegistrationProgress(holdingIdentityShortHash)

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
                        event.config.getConfig(MESSAGING_CONFIG)
                    ).also {
                        it.start()
                    }
                )
                coordinator.updateStatus(LifecycleStatus.UP, "Dependencies are UP and configuration received.")
            }
        }
    }

    private fun deactivate(reason: String) {
        coordinator.updateStatus(LifecycleStatus.DOWN, reason)
        val current = impl
        impl = InactiveImpl
        current.close()
    }

    private object InactiveImpl : InnerMemberOpsClient {
        override fun startRegistration(memberRegistrationRequest: MemberRegistrationRequestDto) =
            throw IllegalStateException(ERROR_MSG)

        override fun checkRegistrationProgress(holdingIdentityShortHash: ShortHash) =
            throw IllegalStateException(ERROR_MSG)

        override fun checkSpecificRegistrationProgress(
            holdingIdentityShortHash: ShortHash,
            registrationRequestId: String,
        ) =
            throw IllegalStateException(ERROR_MSG)

        override fun close() = Unit
    }

    private inner class ActiveImpl(
        val rpcSender: RPCSender<MembershipRpcRequest, MembershipRpcResponse>
    ) : InnerMemberOpsClient {
        override fun startRegistration(memberRegistrationRequest: MemberRegistrationRequestDto): RegistrationRequestProgressDto {
            val requestId = UUID.randomUUID().toString()
            try {
                val request = MembershipRpcRequest(
                    MembershipRpcRequestContext(
                        requestId,
                        clock.instant()
                    ),
                    RegistrationRpcRequest(
                        memberRegistrationRequest.holdingIdentityShortHash.toString(),
                        RegistrationRpcAction.valueOf(memberRegistrationRequest.action.name),
                        memberRegistrationRequest.context.toWire()
                    )
                )

                val response: RegistrationRpcResponse = request.sendRequest()

                return response.toDto()
            } catch (e: TimeoutException) {
                // The request timed out, but we did manage to submit it to Kafka. This might result in a state change
                // in the system, but now we cannot tell whether the other side picked the request up. This is not
                // idempotent (and is equally likely to hit the timeout again on retry anyway), so our best bet is to
                // return success and get the client to check the registration status.
                //
                // This should probably be changed to not use the RPC pattern at all, and instead use an async pattern.
                logger.debug { "Request $requestId timed out for ${memberRegistrationRequest.holdingIdentityShortHash}" }
                return RegistrationRequestProgressDto(
                    requestId,
                    null,
                    RegistrationRpcStatus.SUBMITTED.toString(),
                    "Submitting registration request was successful.",
                    MemberInfoSubmittedDto(
                        mapOf()
                    )
                )

            } catch (e: Exception) {
                logger.warn(
                    "Could not submit registration request for holding identity ID" +
                            " [${memberRegistrationRequest.holdingIdentityShortHash}].", e
                )
                return RegistrationRequestProgressDto(
                    requestId,
                    null,
                    RegistrationRpcStatus.NOT_SUBMITTED.toString(),
                    e.message ?: "No cause was provided for failure.",
                    MemberInfoSubmittedDto(emptyMap())
                )
            }
        }

        override fun checkRegistrationProgress(holdingIdentityShortHash: ShortHash): List<RegistrationRequestStatusDto> {
            return try {
                val request = MembershipRpcRequest(
                    MembershipRpcRequestContext(
                        UUID.randomUUID().toString(),
                        clock.instant()
                    ),
                    RegistrationStatusRpcRequest(holdingIdentityShortHash.toString())
                )

                val result = registrationsResponse(request.sendRequest())
                if (result.isEmpty()) {
                    throw RegistrationProgressNotFoundException(
                        "There are no requests for '$holdingIdentityShortHash' holding identity."
                    )
                }
                result
            } catch (e: RegistrationProgressNotFoundException) {
                throw e
            } catch (e: Exception) {
                logger.warn(
                    "Could not check statuses of registration requests made by holding identity ID" +
                            " [${holdingIdentityShortHash}].", e
                )
                emptyList()
            }
        }

        override fun checkSpecificRegistrationProgress(
            holdingIdentityShortHash: ShortHash,
            registrationRequestId: String,
        ): RegistrationRequestStatusDto? {
            try {
                val request = MembershipRpcRequest(
                    MembershipRpcRequestContext(
                        UUID.randomUUID().toString(),
                        clock.instant()
                    ),
                    RegistrationStatusSpecificRpcRequest(
                        holdingIdentityShortHash.toString(),
                        registrationRequestId,
                    )
                )

                val response: RegistrationStatusResponse = request.sendRequest()
                val status = response.status
                    ?: throw RegistrationProgressNotFoundException(
                        "There is no request with '$registrationRequestId' id in '$holdingIdentityShortHash'."
                    )
                return status.toDto()
            } catch (e: RegistrationProgressNotFoundException) {
                throw e
            } catch (e: Exception) {
                logger.warn(
                    "Could not check status of registration request `$registrationRequestId` made by holding identity ID" +
                            " [${holdingIdentityShortHash}].", e
                )
                return null
            }
        }

        override fun close() = rpcSender.close()

        private fun registrationsResponse(response: RegistrationsStatusResponse): List<RegistrationRequestStatusDto> {
            return response.requests.map {
                it.toDto()
            }
        }

        @Suppress("SpreadOperator")
        private fun RegistrationStatusDetails.toDto(): RegistrationRequestStatusDto =
            RegistrationRequestStatusDto(
                this.registrationId,
                this.registrationSent,
                this.registrationLastModified,
                this.registrationStatus.toDto(),
                MemberInfoSubmittedDto(
                    mapOf(
                        "registrationProtocolVersion" to this.registrationProtocolVersion.toString(),
                        *this.memberProvidedContext.items.map { it.key to it.value }.toTypedArray(),
                    )
                )
            )

        @Suppress("SpreadOperator")
        private fun RegistrationRpcResponse.toDto(): RegistrationRequestProgressDto =
            RegistrationRequestProgressDto(
                this.registrationId,
                this.registrationSent,
                this.registrationStatus.toString(),
                this.reason ?: "Submitting registration request was successful.",
                MemberInfoSubmittedDto(
                    mapOf(
                        "registrationProtocolVersion" to this.registrationProtocolVersion.toString(),
                        *this.memberProvidedContext.items.map { it.key to it.value }.toTypedArray(),
                        *this.additionalInfo.items.map { it.key to it.value }.toTypedArray()
                    )
                )
            )

        private inline fun <reified RESPONSE> MembershipRpcRequest.sendRequest(): RESPONSE {
            try {
                logger.debug { "Sending request: $this" }
                val response = rpcSender.sendRequest(this).getOrThrow(TIMEOUT)
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
            } catch (e: TimeoutException) {
                // If we get a timeout, the other side may well have got the request and may be processing it. Throw a
                // different exception to allow the calling function to decide what it wants to do in this case.
                throw e
            } catch (e: Exception) {
                throw CordaRuntimeException(
                    "Failed to send request and receive response for membership RPC operation. " + e.message, e
                )
            }
        }
    }

    private fun RegistrationStatus.toDto(): RegistrationStatusDto {
        return when (this) {
            RegistrationStatus.NEW -> RegistrationStatusDto.NEW
            RegistrationStatus.PENDING_MEMBER_VERIFICATION -> RegistrationStatusDto.PENDING_MEMBER_VERIFICATION
            RegistrationStatus.PENDING_APPROVAL_FLOW -> RegistrationStatusDto.PENDING_APPROVAL_FLOW
            RegistrationStatus.PENDING_MANUAL_APPROVAL -> RegistrationStatusDto.PENDING_MANUAL_APPROVAL
            RegistrationStatus.PENDING_AUTO_APPROVAL -> RegistrationStatusDto.PENDING_AUTO_APPROVAL
            RegistrationStatus.DECLINED -> RegistrationStatusDto.DECLINED
            RegistrationStatus.APPROVED -> RegistrationStatusDto.APPROVED
        }
    }
}
