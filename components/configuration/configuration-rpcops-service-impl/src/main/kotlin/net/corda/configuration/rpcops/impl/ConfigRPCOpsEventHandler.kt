package net.corda.configuration.rpcops.impl

import net.corda.configuration.read.ConfigurationReadService
import net.corda.configuration.rpcops.ConfigRPCOpsServiceException
import net.corda.configuration.rpcops.impl.v1.ConfigRPCOps
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus.DOWN
import net.corda.lifecycle.LifecycleStatus.ERROR
import net.corda.lifecycle.LifecycleStatus.UP
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent

/** Handles incoming [LifecycleCoordinator] events for [ConfigRPCOpsServiceImpl]. */
internal class ConfigRPCOpsEventHandler(
    private val configRPCOpsService: ConfigRPCOpsServiceImpl,
    private val configReadService: ConfigurationReadService,
    private val configRPCOps: ConfigRPCOps
) : LifecycleEventHandler {
    private var configReadServiceRegistration: RegistrationHandle? = null
    private var configReadServiceHandle: AutoCloseable? = null

    /**
     * Upon receiving configuration from [configReadService], start handling RPC operations related to cluster
     * configuration management.
     *
     * @throws ConfigRPCOpsServiceException TODO - Joel - Describe possible exceptions.
     */
    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> processStartEvent()
            is RegistrationStatusChangeEvent -> processRegistrationStatusChangeEvent(event)
            is StopEvent -> processStopEvent()
        }
    }

    // TODO - Joel - Describe.
    private fun processStartEvent() {
        configReadServiceRegistration?.close()
        configReadServiceRegistration = configRPCOpsService.coordinator.followStatusChangesByName(
            setOf(LifecycleCoordinatorName.forComponent<ConfigurationReadService>())
        )
    }

    // TODO - Joel - Describe.
    private fun processRegistrationStatusChangeEvent(event: RegistrationStatusChangeEvent) {
        if (event.registration == configReadServiceRegistration && event.status == UP) {
            configReadServiceHandle?.close()
            configReadServiceHandle = configReadService.registerForUpdates(::onConfigurationUpdated)
        }
    }

    // TODO - Joel - Describe.
    private fun processStopEvent() {
        // TODO - Joel - Encapsulate this in a method on configRPCOpsRpcSender. Make it implement lifecycle?
        configRPCOps.close()
        configRPCOpsService.coordinator.updateStatus(DOWN)
    }

    private fun onConfigurationUpdated(changedKeys: Set<String>, currentConfigSnapshot: Map<String, SmartConfig>) {
        // TODO - Joel - Use constant here and below.
        if ("corda.boot" in changedKeys) {
            try {
                // TODO - Joel - Handle possible null.
                configRPCOps.start(currentConfigSnapshot["corda.boot"]!!)
            } catch (e: Exception) {
                configRPCOpsService.coordinator.updateStatus(ERROR)
                throw ConfigRPCOpsServiceException("TODO - Joel", e)
            }
            configRPCOpsService.coordinator.updateStatus(UP)
        }
    }
}