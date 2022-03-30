package net.corda.membership.impl.client.lifecycle

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.membership.rpc.request.MembershipRpcRequest
import net.corda.data.membership.rpc.response.MembershipRpcResponse
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.messaging.api.config.toMessagingConfig
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.schema.Schemas.Membership.Companion.MEMBERSHIP_RPC_TOPIC
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG

class MemberOpsClientLifecycleHandler(
    private val publisherFactory: PublisherFactory,
    private val configurationReadService: ConfigurationReadService,
    private val activate: (RPCSender<MembershipRpcRequest, MembershipRpcResponse>, String) -> Unit,
    private val deactivate: (String) -> Unit
) : LifecycleEventHandler {
    companion object {
        const val CLIENT_ID = "membership.ops.rpc"
        const val GROUP_NAME = "membership.ops.rpc"
    }

    // for watching the config changes
    private var configHandle: AutoCloseable? = null

    // for checking the components' health
    private var componentHandle: AutoCloseable? = null

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
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
                deactivate.invoke("Handling the stop event for component.")
            }
            is RegistrationStatusChangeEvent -> {
                when (event.status) {
                    LifecycleStatus.UP -> {
                        configHandle?.close()
                        configHandle = configurationReadService.registerComponentForUpdates(
                            coordinator,
                            setOf(BOOT_CONFIG, MESSAGING_CONFIG)
                        )
                    }
                    else -> {
                        configHandle?.close()
                        deactivate.invoke("Service dependencies have changed status causing this component to deactivate.")
                    }
                }
            }
            is ConfigChangedEvent -> {
                publisherFactory.createRPCSender(
                    RPCConfig(
                        groupName = GROUP_NAME,
                        clientName = CLIENT_ID,
                        requestTopic = MEMBERSHIP_RPC_TOPIC,
                        requestType = MembershipRpcRequest::class.java,
                        responseType = MembershipRpcResponse::class.java
                    ),
                    event.config.toMessagingConfig()
                ).also {
                    it.start()
                    activate.invoke(it, "Dependencies are UP and configuration received.")
                }
            }
        }
    }
}