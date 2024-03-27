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
import net.corda.virtualnode.write.db.VirtualNodeWriteServiceException
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeWriter
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeWriterFactory
import org.slf4j.LoggerFactory

/** Handles incoming [LifecycleCoordinator] events for [VirtualNodeWriteServiceImpl]. */
internal class VirtualNodeWriteEventHandler(
    private val configReadService: ConfigurationReadService,
    private val virtualNodeWriterFactory: VirtualNodeWriterFactory
) : LifecycleEventHandler {

    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

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
        val externalMsgConfig = event.config.getConfig(ConfigKeys.EXTERNAL_MESSAGING_CONFIG)
        val vnodeDatasourceConfig = event.config.getConfig(ConfigKeys.VNODE_DATASOURCE_CONFIG)
        val bootConfig = event.config.getConfig(ConfigKeys.BOOT_CONFIG)

        logger.info("Configuration changed event received")
        try {
            virtualNodeWriter?.close()
            logger.info("Current virtual node writer has been closed")
            virtualNodeWriter = virtualNodeWriterFactory
                .create(msgConfig, externalMsgConfig, vnodeDatasourceConfig, bootConfig)
                .apply { start() }
            logger.info("New virtual node write has been created")

            coordinator.updateStatus(UP)
        } catch (e: Exception) {
            coordinator.updateStatus(ERROR)
            throw VirtualNodeWriteServiceException(
                "Could not start the virtual node writer for handling virtual node creation requests.",
                e
            )
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
                        configReadService.registerComponentForUpdates(
                            coordinator,
                            setOf(
                                ConfigKeys.MESSAGING_CONFIG,
                                ConfigKeys.EXTERNAL_MESSAGING_CONFIG,
                                ConfigKeys.VNODE_DATASOURCE_CONFIG,
                                ConfigKeys.BOOT_CONFIG
                            )
                        )
                }
                ERROR -> coordinator.postEvent(StopEvent(errored = true, reason = "Coordinator stopped. Registering for config updates failed."))
                else -> Unit
            }
        }
    }

    /** Shuts down the service. */
    private fun stop() {
        virtualNodeWriter?.close()
        registrationHandle?.close()
        configUpdateHandle?.close()
    }
}
