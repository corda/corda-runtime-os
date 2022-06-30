package net.corda.membership.impl.persistence.client

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.db.request.MembershipPersistenceRequest
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.response.MembershipPersistenceResponse
import net.corda.data.membership.db.response.MembershipResponseContext
import net.corda.data.membership.db.response.query.QueryFailedResponse
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.schema.Schemas
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.v5.base.concurrent.getOrThrow
import net.corda.v5.base.util.contextLogger
import java.lang.IllegalArgumentException
import java.time.Duration
import java.util.*

abstract class AbstractPersistenceClient(
    coordinatorFactory: LifecycleCoordinatorFactory,
    lifecycleCoordinatorName: LifecycleCoordinatorName,
    private val publisherFactory: PublisherFactory,
    private val configurationReadService: ConfigurationReadService
) : Lifecycle {

    companion object {
        private val logger = contextLogger()
        const val RPC_TIMEOUT_MS = 10000L

        private val clock: Clock = UTCClock()
    }

    abstract val groupName: String
    abstract val clientName: String

    private val coordinator = coordinatorFactory.createCoordinator(
        lifecycleCoordinatorName,
        ::handleEvent
    )
    private var rpcSender: RPCSender<MembershipPersistenceRequest, MembershipPersistenceResponse>? = null

    private var registrationHandle: RegistrationHandle? = null
    private var configHandle: AutoCloseable? = null

    fun buildMembershipRequestContext(holdingIdentity: HoldingIdentity) = MembershipRequestContext(
        clock.instant(),
        UUID.randomUUID().toString(),
        holdingIdentity
    )

    fun MembershipPersistenceRequest.execute(): MembershipPersistenceResponse {
        val sender = rpcSender
        if(sender == null) {
            val failureReason = "Persistence client could not send persistence request because the RPC sender has not been initialised."
            logger.warn(failureReason)
            return MembershipPersistenceResponse(
                MembershipResponseContext(
                    context.requestTimestamp,
                    context.requestId,
                    clock.instant(),
                    context.holdingIdentity
                ),
                QueryFailedResponse(failureReason)
            )
        }
        logger.info("Sending membership persistence RPC request.")

        return try {
            val response = sender
                .sendRequest(this)
                .getOrThrow(Duration.ofMillis(RPC_TIMEOUT_MS))

            with(context) {
                require(holdingIdentity == response.context.holdingIdentity) {
                    "Holding identity in the response received does not match what was sent in the request."
                }
                require(requestTimestamp == response.context.requestTimestamp) {
                    "Request timestamp in the response received does not match what was sent in the request."
                }
                require(requestId == response.context.requestId) {
                    "Request ID in the response received does not match what was sent in the request."
                }
                require(requestTimestamp <= response.context.responseTimestamp) {
                    "Response timestamp is before the request timestamp"
                }
            }
            response
        } catch (e: IllegalArgumentException) {
            MembershipPersistenceResponse(
                MembershipResponseContext(
                    context.requestTimestamp,
                    context.requestId,
                    clock.instant(),
                    context.holdingIdentity
                ),
                QueryFailedResponse("Invalid response. ${e.message}")
            )
        }
        catch (e: Exception) {
            MembershipPersistenceResponse(
                MembershipResponseContext(
                    context.requestTimestamp,
                    context.requestId,
                    clock.instant(),
                    context.holdingIdentity
                ),
                QueryFailedResponse("Exception occurred while sending RPC request. ${e.message}")
            )
        }
    }

    override val isRunning: Boolean
        get() = coordinator.status == LifecycleStatus.UP

    override fun start() {
        logger.info("Starting component.")
        coordinator.start()
    }

    override fun stop() {
        logger.info("Stopping component.")
        coordinator.stop()
    }

    private fun handleEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.info("Received event $event.")
        when (event) {
            is StartEvent -> {
                logger.info("Handling start event.")
                registrationHandle?.close()
                registrationHandle = coordinator.followStatusChangesByName(
                    setOf(
                        LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
                    )
                )
            }
            is StopEvent -> {
                logger.info("Handling stop event.")
                coordinator.updateStatus(
                    LifecycleStatus.DOWN,
                    "Component received stop event."
                )
                registrationHandle?.close()
                registrationHandle = null
                configHandle?.close()
                configHandle = null
                rpcSender?.close()
                rpcSender = null
            }
            is RegistrationStatusChangeEvent -> {
                logger.info("Handling registration changed event.")
                when(event.status) {
                    LifecycleStatus.UP -> {
                        configHandle?.close()
                        configHandle = configurationReadService.registerComponentForUpdates(
                            coordinator,
                            setOf(BOOT_CONFIG, MESSAGING_CONFIG)
                        )
                    }
                    else -> {
                        coordinator.updateStatus(
                            LifecycleStatus.DOWN,
                            "Dependency component is down."
                        )
                    }
                }
            }
            is ConfigChangedEvent -> {
                logger.info("Handling config changed event.")
                rpcSender?.close()
                rpcSender = publisherFactory.createRPCSender(
                    rpcConfig = RPCConfig(
                        groupName = groupName,
                        clientName = clientName,
                        requestTopic = Schemas.Membership.MEMBERSHIP_DB_RPC_TOPIC,
                        requestType = MembershipPersistenceRequest::class.java,
                        responseType = MembershipPersistenceResponse::class.java
                    ),
                    messagingConfig = event.config.getConfig(MESSAGING_CONFIG)
                ).also {
                    it.start()
                }
                coordinator.updateStatus(
                    LifecycleStatus.UP,
                    "Received config and started RPC topic subscription."
                )
            }
        }
    }
}
