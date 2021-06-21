package net.corda.components.examples.config.reader

import com.typesafe.config.Config
import net.corda.libs.configuration.read.ConfigListener
import net.corda.libs.configuration.read.factory.ConfigReadServiceFactory
import net.corda.lifecycle.LifeCycle
import net.corda.lifecycle.LifeCycleCoordinator
import net.corda.lifecycle.LifeCycleEvent
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger


class ConfigReceivedEvent(val currentConfigurationSnapshot: Map<String, Config>) : LifeCycleEvent
class KafkaConfigUpdateEvent(val currentConfigurationSnapshot: Map<String, Config>) : LifeCycleEvent

class ConfigReader(
    private val lifeCycleCoordinator: LifeCycleCoordinator,
@Reference(service = ConfigReadServiceFactory::class)
private val readServiceFactory: ConfigReadServiceFactory
) : LifeCycle {

    companion object {
        private val log: Logger = contextLogger()
        const val KAFKA_CONFIG: String = "corda.kafka"
    }

    private var receivedSnapshot = false

    private val configReadService = readServiceFactory.createReadService()
    private var sub: AutoCloseable? = null

    override val isRunning: Boolean
    get() = receivedSnapshot

    override fun start() {
        val lister = ConfigListener { changedKeys: Set<String>, currentConfigurationSnapshot: Map<String, Config> ->
            if (!receivedSnapshot) {
                log.info("Config read service config snapshot received")
                receivedSnapshot = true
                lifeCycleCoordinator.postEvent(ConfigReceivedEvent(currentConfigurationSnapshot))
            } else {
                log.info("Config read service config update received")
                if (changedKeys.contains(KAFKA_CONFIG)) {
                    log.info("Config update contains kafka config")
                    lifeCycleCoordinator.postEvent(KafkaConfigUpdateEvent(currentConfigurationSnapshot))
                }
            }
            receivedSnapshot = true
        }
        sub = configReadService.registerCallback(lister)
        configReadService.start()
    }

    override fun stop() {
        sub?.close()
        sub = null
    }
}
