package net.corda.membership.impl.persistence.client

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.db.request.MembershipPersistenceRequest
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.response.MembershipPersistenceResponse
import net.corda.data.membership.db.response.query.PersistenceFailedResponse
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
import net.corda.utilities.Either
import net.corda.utilities.time.Clock
import java.util.UUID

abstract class AbstractPersistenceClient(
    coordinatorFactory: LifecycleCoordinatorFactory,
    lifecycleCoordinatorName: LifecycleCoordinatorName,
    private val publisherFactory: PublisherFactory,
    private val configurationReadService: ConfigurationReadService,
    private val clock: Clock,
) : Lifecycle {

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

    internal fun <T> MembershipPersistenceRequest.operation(
        convertResult: (Any?) -> Either<T, String>,
    ): MembershipPersistenceOperationImpl<T> {
        return MembershipPersistenceOperationImpl(
            rpcSender,
            this,
            convertResult,
        )
    }

    fun MembershipPersistenceRequest.execute(): Any? {
        val result = this.operation {
            Either.Left(it)
        }.send()
        return when (result) {
            is Either.Left -> result.a
            is Either.Right -> PersistenceFailedResponse(result.b)
        }
    }

    override val isRunning: Boolean
        get() = coordinator.status == LifecycleStatus.UP

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }

    private fun handleEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                registrationHandle?.close()
                registrationHandle = coordinator.followStatusChangesByName(
                    setOf(
                        LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
                    )
                )
            }
            is StopEvent -> {
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
