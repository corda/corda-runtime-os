package net.corda.virtualnode.common.endpoints

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

/** Registers [LateInitRPCOpsConfigHandler] for updates when [ConfigurationReadService] is ready. */
class LateInitRPCOpsEventHandler(
    private val configReadService: ConfigurationReadService,
    private val lateInitRPCOps: LateInitRPCOps
) : LifecycleEventHandler {

    private var configReadServiceRegistrationHandle: RegistrationHandle? = null
    private var configUpdateHandle: AutoCloseable? = null

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
                    val configHandler =
                        LateInitRPCOpsConfigHandler(coordinator, lateInitRPCOps)
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
        lateInitRPCOps.close()
        configReadServiceRegistrationHandle?.close()
        configUpdateHandle?.close()
    }
}