package net.corda.membership.impl.httprpc.lifecycle

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
import net.corda.membership.impl.httprpc.MembershipRpcOpsClientImpl
import net.corda.messaging.api.config.toMessagingConfig
import net.corda.messaging.api.publisher.RPCSender
import net.corda.messaging.api.subscription.config.RPCConfig
import net.corda.schema.configuration.ConfigKeys.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG

class MembershipRpcOpsClientLifecycleHandler(
    membershipRpcOpsClient: MembershipRpcOpsClientImpl
) : LifecycleEventHandler {
    companion object {
        const val CLIENT_ID = "membership.ops.rpc"
        const val GROUP_NAME = "membership.ops.rpc"
        const val TOPIC_NAME = "membership.rpc.ops"
    }

    // for watching the config changes
    private var configHandle: AutoCloseable? = null
    // for checking the components' health
    private var componentHandle: AutoCloseable? = null

    private val publisherFactory = membershipRpcOpsClient.publisherFactory

    private val configurationReadService = membershipRpcOpsClient.configurationReadService

    private var _rpcSender: RPCSender<MembershipRpcRequest, MembershipRpcResponse>? = null

    /**
     * RPC sender for the message bus. Recreated after every [MESSAGING_CONFIG] change.
     */
    val rpcSender: RPCSender<MembershipRpcRequest, MembershipRpcResponse>
        get() = _rpcSender ?: throw IllegalArgumentException("RPC sender is not initialized.")

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when(event) {
            is StartEvent -> handleStartEvent(coordinator)
            is StopEvent -> handleStopEvent()
            is RegistrationStatusChangeEvent -> handleRegistrationChangeEvent(event, coordinator)
            is ConfigChangedEvent -> handleConfigChange(event, coordinator)
        }
    }

    private fun handleStartEvent(coordinator: LifecycleCoordinator) {
        componentHandle?.close()
        componentHandle = coordinator.followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
            )
        )
    }

    private fun handleStopEvent() {
        componentHandle?.close()
        configHandle?.close()
        _rpcSender?.close()
        _rpcSender = null
    }

    private fun handleRegistrationChangeEvent(
        event: RegistrationStatusChangeEvent,
        coordinator: LifecycleCoordinator
    ) {
        when (event.status) {
            LifecycleStatus.UP -> {
                configHandle?.close()
                configHandle = configurationReadService.registerComponentForUpdates(
                    coordinator,
                    setOf(BOOT_CONFIG, MESSAGING_CONFIG)
                )
            }
            else -> {
                coordinator.updateStatus(LifecycleStatus.DOWN)
                configHandle?.close()
            }
        }
    }

    // re-creates the rpc sender with the new config, sets the lifecycle status to UP when the rpc sender is ready for the first time
    private fun handleConfigChange(event: ConfigChangedEvent, coordinator: LifecycleCoordinator) {
        _rpcSender?.close()
        _rpcSender = publisherFactory.createRPCSender(
            RPCConfig(
                groupName = GROUP_NAME,
                clientName = CLIENT_ID,
                requestTopic = TOPIC_NAME,
                requestType = MembershipRpcRequest::class.java,
                responseType = MembershipRpcResponse::class.java
            ),
            event.config.toMessagingConfig()
        )
        _rpcSender?.start()
        if(coordinator.status != LifecycleStatus.UP) {
            coordinator.updateStatus(LifecycleStatus.UP)
        }
    }
}