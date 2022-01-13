package net.corda.configuration.rpcops.impl

import net.corda.configuration.read.ConfigurationReadService
import net.corda.configuration.rpcops.impl.v1.ConfigRPCOpsInternal
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.LifecycleStatus.DOWN
import net.corda.lifecycle.LifecycleStatus.ERROR
import net.corda.lifecycle.LifecycleStatus.UP
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent

/** Handles incoming [LifecycleCoordinator] events for [ConfigRPCOpsServiceImpl]. */
internal class ConfigRPCOpsEventHandler(
    private val configReadService: ConfigurationReadService,
    private val configRPCOps: ConfigRPCOpsInternal
) : LifecycleEventHandler {

    private var configReadServiceRegistrationHandle: AutoCloseable? = null
    private var configUpdateHandle: AutoCloseable? = null

    /**
     * Upon receiving configuration from [configReadService], starts handling RPC operations related to cluster
     * configuration management.
     */
    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> followConfigReadServiceStatus(coordinator)
            is RegistrationStatusChangeEvent -> tryRegisteringForConfigUpdates(coordinator, event)
            is StopEvent -> stop(coordinator, DOWN)
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
                    val configHandler = ConfigRPCOpsConfigHandler(coordinator, configRPCOps)
                    configUpdateHandle?.close()
                    configUpdateHandle = configReadService.registerForUpdates(configHandler)
                }
                ERROR -> {
                    stop(coordinator, ERROR)
                }
                else -> Unit
            }
        }
    }

    /** Shuts down the service. */
    private fun stop(coordinator: LifecycleCoordinator, status: LifecycleStatus) {
        configRPCOps.close()
        configReadServiceRegistrationHandle?.close()
        configUpdateHandle?.close()
        coordinator.updateStatus(status)
    }
}