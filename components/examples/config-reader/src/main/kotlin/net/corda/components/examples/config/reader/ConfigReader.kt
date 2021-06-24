package net.corda.components.examples.config.reader

import com.typesafe.config.Config
import net.corda.libs.configuration.read.ConfigListener
import net.corda.libs.configuration.read.ConfigReadService
import net.corda.libs.configuration.read.factory.ConfigReadServiceFactory
import net.corda.lifecycle.LifeCycle
import net.corda.lifecycle.LifeCycleCoordinator
import net.corda.lifecycle.LifeCycleEvent
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger


class ConfigReceivedEvent(val currentConfigurationSnapshot: Map<String, Config>) : LifeCycleEvent
class MessagingConfigUpdateEvent(val currentConfigurationSnapshot: Map<String, Config>) : LifeCycleEvent

class ConfigReader(
    private val lifeCycleCoordinator: LifeCycleCoordinator,
    @Reference(service = ConfigReadServiceFactory::class)
private val readServiceFactory: ConfigReadServiceFactory
) : LifeCycle {

    companion object {
        private val log: Logger = contextLogger()
        const val MESSAGING_CONFIG: String = "corda.messaging"
    }

    private var receivedSnapshot = false

    private var configReadService: ConfigReadService? = null
    private var sub: AutoCloseable? = null
    private var bootstrapConfig: Config? = null

    override val isRunning: Boolean
    get() = receivedSnapshot

    fun start(bootstrapConfig: Config) {
        this.bootstrapConfig = bootstrapConfig
        this.start()
    }

    override fun start() {
        if(bootstrapConfig != null){
            configReadService = readServiceFactory.createReadService(bootstrapConfig!!)
            val lister = ConfigListener { changedKeys: Set<String>, currentConfigurationSnapshot: Map<String, Config> ->
                if (!receivedSnapshot) {
                    log.info("Config read service config snapshot received")
                    receivedSnapshot = true
                    lifeCycleCoordinator.postEvent(ConfigReceivedEvent(currentConfigurationSnapshot))
                } else {
                    log.info("Config read service config update received")
                    if (changedKeys.contains(MESSAGING_CONFIG)) {
                        log.info("Config update contains kafka config")
                        lifeCycleCoordinator.postEvent(MessagingConfigUpdateEvent(currentConfigurationSnapshot))
                    }
                }

                receivedSnapshot = true
            }
            sub = configReadService!!.registerCallback(lister)
            configReadService!!.start()
        } else {
            val message = "Use the other start method available and pass in the bootstrap configuration"
            log.error(message)
            throw CordaRuntimeException(message)
        }
    }

    override fun stop() {
        sub?.close()
        sub = null
    }
}
