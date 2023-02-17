package net.corda.db.connection.manager.impl

import net.corda.db.connection.manager.DBConfigurationException
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.ErrorEvent
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.minutes
import org.slf4j.LoggerFactory

class DbConnectionManagerEventHandler(
    private val dbConnectionManager: DbConnectionManager
) : LifecycleEventHandler {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        private const val dbCheckTimerKey = "CheckDbEvent"
        private val timeBetweenDbChecks = 1.minutes
    }

    private lateinit var bootstrapConfig: SmartConfig

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                logger.debug { "DbConnectionManager starting up." }
            }
            is BootstrapConfigProvided -> {
                logger.debug { "Bootstrap config received: ${event.config}" }
                processBootstrapConfig(event.config, coordinator)
            }
            is StopEvent -> {
                logger.debug { "DbConnectionManager stopping." }
            }
            is ErrorEvent -> {
                logger.error(
                    "An error occurred in the DbConnectionManager: ${event.cause.message}.",
                    event.cause
                )
            }
            is CheckDbEvent -> {
                checkDb(coordinator)
            }
        }
    }

    fun scheduleNextDbCheck(coordinator: LifecycleCoordinator) {
        // Turned off while we investigate a failing test
        // coordinator.setTimer(dbCheckTimerKey, timeBetweenDbChecks.toMillis()) { key -> CheckDbEvent(key) }
    }

    @Synchronized
    private fun checkDb(coordinator: LifecycleCoordinator) {
        if (dbConnectionManager.testAllConnections()) {
            coordinator.updateStatus(LifecycleStatus.UP, "DB check passed")
        } else {
            coordinator.updateStatus(LifecycleStatus.DOWN, "DB check failed")
        }
        scheduleNextDbCheck(coordinator)
    }

    @Synchronized
    private fun processBootstrapConfig(config: SmartConfig, coordinator: LifecycleCoordinator) {
        if (this::bootstrapConfig.isInitialized) {
            logger.info("New bootstrap configuration received: $config, Old configuration: $bootstrapConfig")
            if (bootstrapConfig != config) {
                val errorString = "An attempt was made to set the bootstrap configuration twice with " +
                        "different config. Current: $bootstrapConfig, New: $config"
                logger.error(errorString)
                throw DBConfigurationException(errorString)
            }
        } else {
            bootstrapConfig = config
            // initialise the DB connections Repo
            dbConnectionManager.initialise(bootstrapConfig)
            // component is ready
            coordinator.updateStatus(LifecycleStatus.UP)
        }
    }
}
