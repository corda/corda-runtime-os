package net.corda.cpi.upload.endpoints.internal

import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.LifecycleStatus.ERROR
import net.corda.lifecycle.LifecycleStatus.UP
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent

/** Handles incoming [LifecycleCoordinator] events for [VirtualNodeRPCOpsServiceImpl]. */
class CpiUploadRPCOpsEventHandler(
    private val configReadService: ConfigurationReadService,
    private val cpiUploadRPCOps: CpiUploadRPCOpsInternal
) : LifecycleEventHandler {

    private var configReadServiceRegistrationHandle: RegistrationHandle? = null
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
                    val configHandler = CpiUploadRPCOpsConfigHandler(coordinator, cpiUploadRPCOps)
                    configUpdateHandle?.close()
                    configUpdateHandle = configReadService.registerForUpdates(configHandler)
                }
                ERROR -> coordinator.postEvent(StopEvent(errored = true))
                else -> Unit
            }
        }
    }

    /** Shuts down the service. */
    private fun stop() {
        cpiUploadRPCOps.close()
        configReadServiceRegistrationHandle?.close()
        configUpdateHandle?.close()
    }
}