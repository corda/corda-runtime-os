package net.corda.virtualnode.write.db.impl

import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.virtualnode.write.VirtualNodeWriter
import net.corda.libs.virtualnode.write.VirtualNodeWriterFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus.ERROR
import net.corda.lifecycle.LifecycleStatus.UP
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent

/** Handles incoming [LifecycleCoordinator] events for [VirtualNodeWriteServiceImpl]. */
internal class VirtualNodeWriteEventHandler(
    private val configReadService: ConfigurationReadService,
    private val virtualNodeWriterFactory: VirtualNodeWriterFactory
) : LifecycleEventHandler {

    private var configReadServiceRegistrationHandle: AutoCloseable? = null
    private var configUpdateHandle: AutoCloseable? = null
    internal var virtualNodeWriter: VirtualNodeWriter? = null

    /**
     * Upon receiving configuration from [configReadService], starts handling Kafka messages related to virtual node
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
                    val configHandler = VirtualNodeWriteConfigHandler(this, coordinator, virtualNodeWriterFactory)
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
            virtualNodeWriter?.stop()
            virtualNodeWriter = null
            configReadServiceRegistrationHandle?.close()
            configUpdateHandle?.close()
        }
    }