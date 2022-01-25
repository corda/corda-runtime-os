package net.corda.db.connection.manager.impl

import net.corda.db.connection.manager.DbConnectionsRepository
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.ErrorEvent
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug

class DbConnectionManagerEventHandler(
    private val connectionsRepository: DbConnectionsRepository
) : LifecycleEventHandler {

    private companion object {
        private val logger = contextLogger()
    }

    private lateinit var bootstrapConfig: SmartConfig

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                logger.debug { "DbConnectionManager starting up." }
            }
            is BootstrapConfigProvided -> {
                logger.debug { "Bootstrap config received: ${event.config}" }
                // process bootstrap config
                if(this::bootstrapConfig.isInitialized) {
                    val errorString = "An attempt was made to set the bootstrap configuration twice with " +
                            "different config. Current: $bootstrapConfig, New: ${event.config}"
                    logger.error(errorString)
                    throw DBConfigurationException(errorString)
                } else {
                    bootstrapConfig = event.config
                    // initialise the DB connections Repo
                    connectionsRepository.initialise(bootstrapConfig)
                    // component is ready
                    coordinator.updateStatus(LifecycleStatus.UP)
                }
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
        }
    }
}
