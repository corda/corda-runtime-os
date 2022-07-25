package net.corda.libs.virtualnode.maintenance.rpcops.impl.v1

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpi.upload.endpoints.service.CpiUploadRPCOpsService
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.helper.getConfig
import net.corda.libs.virtualnode.maintenance.endpoints.v1.VirtualNodeMaintenanceRPCOps
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.util.contextLogger

internal class VirtualNodeMaintenanceRPCOpsHandler(
    private val configReadService: ConfigurationReadService,
    private val virtualNodeMaintenanceRPCOps: VirtualNodeMaintenanceRPCOpsImpl
) : LifecycleEventHandler {

    private var cpiUploadRPCOpsServiceRegistrationHandle: RegistrationHandle? = null

    private companion object {
        val requiredKeys = setOf(ConfigKeys.MESSAGING_CONFIG, ConfigKeys.RPC_CONFIG)
        val logger = contextLogger()
    }
    private var configReadServiceRegistrationHandle: AutoCloseable? = null
    private var configUpdateHandle: AutoCloseable? = null

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> onStartEvent(coordinator)
            is RegistrationStatusChangeEvent -> onRegistrationStatusChangeEvent(event, coordinator)
            is StopEvent -> onStopEvent(coordinator)
            is BootstrapConfigEvent -> onConfigChangedEvent(
                coordinator,
                mapOf(ConfigKeys.RPC_CONFIG to event.bootstrapConfig),
                setOf(ConfigKeys.RPC_CONFIG)
            )
            is ConfigChangedEvent -> onConfigChangedEvent(coordinator, event.config, event.keys)
        }
    }

    private fun onStartEvent(coordinator: LifecycleCoordinator) {
        configReadServiceRegistrationHandle?.close()
        configReadServiceRegistrationHandle = coordinator.followStatusChangesByName(
            setOf(LifecycleCoordinatorName.forComponent<ConfigurationReadService>())
        )
        cpiUploadRPCOpsServiceRegistrationHandle = coordinator.followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<CpiUploadRPCOpsService>()
            )
        )
    }

    private fun onRegistrationStatusChangeEvent(
        event: RegistrationStatusChangeEvent,
        coordinator: LifecycleCoordinator
    ) {
        logger.info("Changing ${VirtualNodeMaintenanceRPCOps::class.java.simpleName} state to: ${event.status}")
        logger.info("Changing ${VirtualNodeMaintenanceRPCOps::class.java.simpleName} registration to: ${event.registration}")
        if (event.status == LifecycleStatus.ERROR) {
            closeResources()
        }
        if (event.registration == configReadServiceRegistrationHandle) {
            when (event.status) {
                LifecycleStatus.UP -> {
                    configUpdateHandle?.close()
                    configUpdateHandle = configReadService.registerComponentForUpdates(coordinator, requiredKeys)
                }
                LifecycleStatus.ERROR -> coordinator.postEvent(StopEvent(errored = true))
                else -> Unit
            }
        }
        coordinator.updateStatus(event.status)
    }

    private fun onConfigChangedEvent(coordinator: LifecycleCoordinator, config: Map<String, SmartConfig>, changedKeys: Set<String>) {
        if (requiredKeys.all { it in config.keys } and changedKeys.any { it in requiredKeys }) {
            val rpcConfig = config.getConfig(ConfigKeys.RPC_CONFIG)
            val messagingConfig = config.getConfig(ConfigKeys.MESSAGING_CONFIG)
            if (rpcConfig.hasPath(ConfigKeys.RPC_ENDPOINT_TIMEOUT_MILLIS)) {
                val timeoutMillis = rpcConfig.getInt(ConfigKeys.RPC_ENDPOINT_TIMEOUT_MILLIS)
                virtualNodeMaintenanceRPCOps.setTimeout(timeoutMillis)
            }
            try {
                virtualNodeMaintenanceRPCOps.configureRPCSender(messagingConfig)
            } catch (e: Exception) {
                coordinator.updateStatus(LifecycleStatus.ERROR)
                throw VirtualNodeRPCMaintenanceOpsServiceException(
                    "Could not start the RPC sender for incoming HTTP RPC virtual node management requests", e
                )
            }
        }
        if (virtualNodeMaintenanceRPCOps.isRunning) {
            coordinator.updateStatus(LifecycleStatus.UP)
        }
    }

    private fun onFirstConfigEvent(coordinator: LifecycleCoordinator, event: BootstrapConfigEvent) {
        val config = event.bootstrapConfig
        onConfigChangedEvent(coordinator, mapOf(ConfigKeys.RPC_CONFIG to config), setOf(ConfigKeys.RPC_CONFIG))
    }

    private fun onStopEvent(coordinator: LifecycleCoordinator) {
        closeResources()
        coordinator.updateStatus(LifecycleStatus.DOWN)
    }

    private fun closeResources() {
        cpiUploadRPCOpsServiceRegistrationHandle?.close()
        cpiUploadRPCOpsServiceRegistrationHandle = null
    }
}

internal data class BootstrapConfigEvent(val bootstrapConfig: SmartConfig) : LifecycleEvent
