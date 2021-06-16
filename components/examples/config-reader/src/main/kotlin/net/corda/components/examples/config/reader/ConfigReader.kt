package net.corda.components.examples.config.reader

import com.typesafe.config.Config
import net.corda.libs.configuration.read.ConfigUpdate
import net.corda.libs.configuration.read.factory.ConfigReadServiceFactory
import net.corda.lifecycle.LifeCycle
import net.corda.lifecycle.LifeCycleCoordinator
import net.corda.lifecycle.LifeCycleEvent
import net.corda.v5.base.util.contextLogger
import org.slf4j.Logger


object ConfigReceivedEvent : LifeCycleEvent
object KafkaConfigUpdateEvent : LifeCycleEvent

class ConfigReader(
    private val lifeCycleCoordinator: LifeCycleCoordinator,
    private val readServiceFactory: ConfigReadServiceFactory
) : ConfigUpdate, LifeCycle {
    companion object {
        private val log: Logger = contextLogger()
        const val KAFKA_CONFIG: String = "corda.kafka"
    }

    var receivedSnapshot = false

    private val configReadService = readServiceFactory.createReadService()
    override var isRunning: Boolean = false

    override fun start() {
        if (!isRunning) {
            log.info("Starting config read service with callback")
            configReadService.registerCallback(this)
            configReadService.start()
            isRunning = true
        }
    }

    override fun stop() {
        //TODO how can we stop this?
        log.info("Stopping config read service")
        isRunning = false
    }

    fun getAllConfiguration(): Map<String, Config> {
        return configReadService.getAllConfiguration()
    }

    fun getConfiguration(key: String): Config {
        return configReadService.getConfiguration(key)
    }

    fun isReady(): Boolean {
        return receivedSnapshot
    }

    override fun onUpdate(updatedConfig: Map<String, Config>) {
        if (!receivedSnapshot) {
            log.info("Config read service config snapshot received")
            receivedSnapshot = true
            lifeCycleCoordinator.postEvent(ConfigReceivedEvent)
        } else {
            log.info("Config read service config update received")
            if (updatedConfig[KAFKA_CONFIG] != null) {
                log.info("Config update contains kafka config")
                lifeCycleCoordinator.postEvent(KafkaConfigUpdateEvent)
            }
        }
    }

}
