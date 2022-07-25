package net.corda.virtualnode.rpcops.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.helper.getConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus.ERROR
import net.corda.lifecycle.LifecycleStatus.UP
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.schema.configuration.ConfigKeys
import net.corda.schema.configuration.ConfigKeys.RPC_CONFIG
import net.corda.schema.configuration.ConfigKeys.RPC_ENDPOINT_TIMEOUT_MILLIS
import net.corda.virtualnode.rpcops.VirtualNodeRPCOpsServiceException
import net.corda.virtualnode.rpcops.impl.v1.VirtualNodeRPCOpsInternal

/** Handles incoming [LifecycleCoordinator] events for [VirtualNodeRPCOpsServiceImpl]. */
internal class VirtualNodeRPCOpsEventHandler(
    private val configReadService: ConfigurationReadService,
    private val virtualNodeRPCOps: VirtualNodeRPCOpsInternal
) : LifecycleEventHandler {
    private companion object {
        val requiredKeys = setOf(ConfigKeys.MESSAGING_CONFIG, ConfigKeys.RPC_CONFIG)
    }

    private var configReadServiceRegistrationHandle: AutoCloseable? = null
    private var configUpdateHandle: AutoCloseable? = null

    /**
     * Upon receiving configuration from [configReadService], starts handling RPC operations related to virtual node
     * creation.
     */
    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> followConfigReadServiceStatus(coordinator)
            is RegistrationStatusChangeEvent -> tryRegisteringForConfigUpdates(coordinator, event)
            is StopEvent -> stop()
            is ConfigChangedEvent -> onConfigChangedEvent(coordinator, event)
        }
    }

    /**
     * When [RPC_CONFIG] configuration is received, updates [virtualNodeRPCOps]'s request timeout and creates and
     * starts [virtualNodeRPCOps]'s RPC sender if the relevant keys are present.
     *
     * After updating [virtualNodeRPCOps], sets [coordinator]'s status to UP if [virtualNodeRPCOps] is running.
     *
     * @throws VirtualNodeRPCOpsServiceException If [RPC_CONFIG] is in the [changedKeys], but no corresponding
     * configuration is provided in [config].
     */
    private fun onConfigChangedEvent(coordinator: LifecycleCoordinator, event: ConfigChangedEvent) {
        val config = event.config
        val changedKeys = event.keys

        if (requiredKeys.all { it in config.keys } and changedKeys.any { it in requiredKeys }) {
            processRpcConfig(config, coordinator)
        }
        if (virtualNodeRPCOps.isRunning) {
            coordinator.updateStatus(UP)
        }
    }

    /**
     * If [RPC_ENDPOINT_TIMEOUT_MILLIS] is in [configSnapshot], updates [virtualNodeRPCOps]'s request
     * timeout.
     *
     * If [BOOTSTRAP_SERVER] is in [configSnapshot], creates and starts [virtualNodeRPCOps]'s RPC sender.
     *
     * @throws VirtualNodeRPCOpsServiceException If [configSnapshot] does not contain any config for key [RPC_CONFIG],
     * or if [virtualNodeRPCOps]'s RPC sender could not be started.
     */
    private fun processRpcConfig(
        config: Map<String, SmartConfig>,
        coordinator: LifecycleCoordinator
    ) {
        val rpcConfig = config.getConfig(RPC_CONFIG)
        val messagingConfig = config.getConfig(ConfigKeys.MESSAGING_CONFIG)
        if (rpcConfig.hasPath(ConfigKeys.RPC_ENDPOINT_TIMEOUT_MILLIS)) {
            val timeoutMillis = rpcConfig.getInt(ConfigKeys.RPC_ENDPOINT_TIMEOUT_MILLIS)
            virtualNodeRPCOps.setTimeout(timeoutMillis)
        }
        try {
            virtualNodeRPCOps.createAndStartRpcSender(messagingConfig)
        } catch (e: Exception) {
            coordinator.updateStatus(ERROR)
            throw VirtualNodeRPCOpsServiceException(
                "Could not start the RPC sender for incoming HTTP RPC virtual node management requests", e
            )
        }
    }

    /** Starts tracking the status of the [ConfigurationReadService]. */
    private fun followConfigReadServiceStatus(coordinator: LifecycleCoordinator) {
        configReadServiceRegistrationHandle?.close()
        configReadServiceRegistrationHandle = coordinator.followStatusChangesByName(
            setOf(LifecycleCoordinatorName.forComponent<ConfigurationReadService>())
        )
    }

    /** If the [ConfigurationReadService] comes up, registers to receive updates. */
    private fun tryRegisteringForConfigUpdates(
        coordinator: LifecycleCoordinator,
        event: RegistrationStatusChangeEvent
    ) {
        if (event.registration == configReadServiceRegistrationHandle) {
            when (event.status) {
                UP -> {
                    configUpdateHandle?.close()
                    configUpdateHandle = configReadService.registerComponentForUpdates(coordinator, requiredKeys)
                }
                ERROR -> coordinator.postEvent(StopEvent(errored = true))
                else -> Unit
            }
        }
    }

    /** Shuts down the service. */
    private fun stop() {
        virtualNodeRPCOps.close()
        configReadServiceRegistrationHandle?.close()
        configUpdateHandle?.close()
    }
}
