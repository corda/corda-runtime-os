package net.corda.virtualnode.write.db.impl

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.db.connection.manager.DbConnectionManager
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
import net.corda.schema.configuration.MessagingConfig
import net.corda.virtualnode.write.db.VirtualNodeWriteServiceException
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeWriter
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeWriterFactory

/** Handles incoming [LifecycleCoordinator] events for [VirtualNodeWriteServiceImpl]. */
internal class VirtualNodeWriteEventHandler(
    private val configReadService: ConfigurationReadService,
    private val virtualNodeWriterFactory: VirtualNodeWriterFactory
) : LifecycleEventHandler {

    private var registrationHandle: AutoCloseable? = null
    private var configUpdateHandle: AutoCloseable? = null
    internal var virtualNodeWriter: VirtualNodeWriter? = null

    /**
     * Upon receiving configuration from [configReadService], starts handling Kafka messages related to virtual node
     * creation.
     */
    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> onStartEvent(coordinator)
            is RegistrationStatusChangeEvent -> tryRegisteringForConfigUpdates(coordinator, event)
            is ConfigChangedEvent -> onConfigChangedEvent(coordinator, event)
            is StopEvent -> stop()
        }
    }

    private fun onConfigChangedEvent(coordinator: LifecycleCoordinator, event: ConfigChangedEvent) {
        val msgConfig = event.config.getConfig(ConfigKeys.MESSAGING_CONFIG)

        if (msgConfig.hasPath(MessagingConfig.Bus.KAFKA_BOOTSTRAP_SERVERS)) {
            try {
                virtualNodeWriter = virtualNodeWriterFactory
                    .create(msgConfig)
                    .apply { start() }
                coordinator.updateStatus(UP)
            } catch (e: Exception) {
                coordinator.updateStatus(ERROR)
                throw VirtualNodeWriteServiceException(
                    "Could not start the virtual node writer for handling virtual node creation requests.", e
                )
            }
        }
    }

    /** Starts tracking the status of the [ConfigurationReadService]. */
    private fun onStartEvent(coordinator: LifecycleCoordinator) {
        registrationHandle?.close()
        registrationHandle = coordinator.followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                LifecycleCoordinatorName.forComponent<DbConnectionManager>()
            )
        )
    }

    /** If the [ConfigurationReadService] comes up, registers to receive updates. */
    private fun tryRegisteringForConfigUpdates(
        coordinator: LifecycleCoordinator,
        event: RegistrationStatusChangeEvent
    ) {
        if (event.registration == registrationHandle) {
            when (event.status) {
                UP -> {
                    configUpdateHandle?.close()
                    configUpdateHandle =
                        configReadService.registerComponentForUpdates(coordinator, setOf(ConfigKeys.MESSAGING_CONFIG))
                }
                ERROR -> coordinator.postEvent(StopEvent(errored = true))
                else -> Unit
            }
        }
    }

    /** Shuts down the service. */
    private fun stop() {
        virtualNodeWriter?.stop()
        registrationHandle?.close()
        configUpdateHandle?.close()
    }
}
