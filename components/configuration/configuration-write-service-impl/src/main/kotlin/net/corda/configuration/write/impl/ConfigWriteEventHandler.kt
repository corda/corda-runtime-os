package net.corda.configuration.write.impl

import net.corda.configuration.write.ConfigWriteServiceException
import net.corda.configuration.write.impl.dbutils.DBUtils
import net.corda.libs.configuration.write.persistent.PersistentConfigWriter
import net.corda.libs.configuration.write.persistent.PersistentConfigWriterFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus.DOWN
import net.corda.lifecycle.LifecycleStatus.ERROR
import net.corda.lifecycle.LifecycleStatus.UP
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent

/** Handles incoming [LifecycleCoordinator] events for [ConfigWriteServiceImpl]. */
internal class ConfigWriteEventHandler(
    private val configWriterFactory: PersistentConfigWriterFactory,
    private val dbUtils: DBUtils
) : LifecycleEventHandler {
    private var configWriter: PersistentConfigWriter? = null

    /**
     * Upon [StartProcessingEvent], starts processing cluster configuration updates. Upon [StopEvent], stops processing
     * them.
     *
     * @throws ConfigWriteServiceException If multiple [StartProcessingEvent]s are received, or if the creation of the
     *  subscription fails.
     */
    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> Unit // We cannot start processing updates until we have the required service config.

            is StartProcessingEvent -> {
                if (configWriter != null) {
                    throw ConfigWriteServiceException("An attempt was made to start processing twice.")
                }

                tryOrError(coordinator, "Could not connect to cluster database.") {
                    dbUtils.checkClusterDatabaseConnection(event.config)
                }

                tryOrError(coordinator, "Could not subscribe to config management requests.") {
                    // TODO - Joel - I should be pulling this config out of the DB, and check diff with stuff from config read at startup. Raise separate JIRA.
                    configWriter = configWriterFactory
                        .create(event.config, event.instanceId)
                        .apply { start() }
                }

                coordinator.updateStatus(UP)
            }

            is StopEvent -> {
                configWriter?.stop()
                coordinator.updateStatus(DOWN)
            }
        }
    }

    private fun tryOrError(coordinator: LifecycleCoordinator, errMsg: String, action: () -> Unit) {
        try {
            action()
        } catch (e: Exception) {
            coordinator.updateStatus(ERROR)
            throw ConfigWriteServiceException(errMsg, e)
        }
    }
}