package net.corda.membership.impl.client

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.membership.rpc.request.*
import net.corda.data.membership.rpc.response.MGMGroupPolicyResponse
import net.corda.data.membership.rpc.response.MembershipRpcResponse
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
import net.corda.membership.client.MGMOpsClient
import net.corda.membership.client.dto.MGMGenerateGroupPolicyResponseDto
import net.corda.membership.client.dto.MemberInfoSubmittedDto
import net.corda.membership.lib.impl.MemberInfoExtension.Companion.isMgm
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import java.time.Instant
import java.util.*

@Component(service = [MGMOpsClient::class])
class MGMOpsClientImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PublisherFactory::class)
    val publisherFactory: PublisherFactory,
    @Reference(service = ConfigurationReadService::class)
    val configurationReadService: ConfigurationReadService,
    @Reference(service = MembershipGroupReaderProvider::class)
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
) : MGMOpsClient {

    companion object {
        private val logger: Logger = contextLogger()
        const val ERROR_MSG = "Service is in an incorrect state for calling."

        const val CLIENT_ID = "membership.ops.rpc"
        const val GROUP_NAME = "membership.ops.rpc"

        private val clock = UTCClock()

        private val objectMapper = ObjectMapper()
    }

    private interface InnerMGMOpsClient : AutoCloseable {
        fun generateGroupPolicy(holdingIdentityId: String): MGMGenerateGroupPolicyResponseDto
    }

    private var impl: InnerMGMOpsClient = InactiveImpl

    // for watching the config changes
    private var configHandle: AutoCloseable? = null

    // for checking the components' health
    private var componentHandle: AutoCloseable? = null

    private val coordinator = coordinatorFactory.createCoordinator<MGMOpsClient>(::processEvent)

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

    override fun generateGroupPolicy(holdingIdentityId: String) =
        impl.generateGroupPolicy(holdingIdentityId)

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
                        event.config.getConfig(ConfigKeys.MESSAGING_CONFIG)
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

    private object InactiveImpl : InnerMGMOpsClient {
        override fun generateGroupPolicy(holdingIdentityId: String) =
            throw IllegalStateException(ERROR_MSG)

        override fun close() = Unit
    }

    private inner class ActiveImpl(
        val rpcSender: RPCSender<MembershipRpcRequest, MembershipRpcResponse>
    ) : InnerMGMOpsClient {
        override fun generateGroupPolicy(holdingIdentityId: String): MGMGenerateGroupPolicyResponseDto {

            val holdingIdentity = virtualNodeInfoReadService.getById(holdingIdentityId)?.holdingIdentity
                ?: throw CordaRuntimeException("Could not find holding identity associated with member.")

            val reader = membershipGroupReaderProvider.getGroupReader(holdingIdentity)

            val filteredMembers = reader.lookup(MemberX500Name.parse(holdingIdentity.x500Name))?:throw CordaRuntimeException("")

            if(filteredMembers.isMgm) {

                val request = MembershipRpcRequest(
                    MembershipRpcRequestContext(
                        UUID.randomUUID().toString(),
                        clock.instant()
                    ),
                    MGMGroupPolicyRequest(holdingIdentityId)
                )

                return generateGroupPolicyResponse(request.sendRequest())
            }

            return  MGMGenerateGroupPolicyResponseDto(Instant.now(),"FAILED", MemberInfoSubmittedDto(emptyMap()), MemberInfoSubmittedDto(emptyMap()))

        }

        override fun close() = rpcSender.close()

        @Suppress("SpreadOperator")
        private fun generateGroupPolicyResponse(response: MGMGroupPolicyResponse): MGMGenerateGroupPolicyResponseDto =
            MGMGenerateGroupPolicyResponseDto(
                response.requestSent,
                response.mgmProvidedContext.toString(),
                MemberInfoSubmittedDto(
                    mapOf(
                        *response.memberProvidedContext.items.map { it.key to it.value }.toTypedArray(),
                        *response.additionalInfo.items.map { it.key to it.value }.toTypedArray()
                    )
                ),
                MemberInfoSubmittedDto(
                    mapOf(
                        *response.mgmProvidedContext.items.map { it.key to it.value }.toTypedArray(),
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