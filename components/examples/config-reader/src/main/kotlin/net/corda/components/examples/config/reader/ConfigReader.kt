package net.corda.components.examples.config.reader

import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.read.ConfigListener
import net.corda.libs.configuration.read.ConfigReader
import net.corda.libs.configuration.read.factory.ConfigReaderFactory
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger


class ConfigReceivedEvent(val currentConfigurationSnapshot: Map<String, SmartConfig>) : LifecycleEvent
class MessagingConfigUpdateEvent(val currentConfigurationSnapshot: Map<String, SmartConfig>) : LifecycleEvent

class ConfigReader(
    private val lifeCycleCoordinator: LifecycleCoordinator,
    @Reference(service = ConfigReaderFactory::class)
private val readServiceFactory: ConfigReaderFactory
) : Lifecycle {

    companion object {
        private val log: Logger = contextLogger()
        const val MESSAGING_CONFIG: String = "corda.messaging"
    }

    private var receivedSnapshot = false

    private var configReader: ConfigReader? = null
    private var sub: AutoCloseable? = null
    private var bootstrapConfig: SmartConfig? = null

    override val isRunning: Boolean
    get() = receivedSnapshot

    fun start(bootstrapConfig: SmartConfig) {
        this.bootstrapConfig = bootstrapConfig
        this.start()
    }

    override fun start() {
        if(bootstrapConfig != null){
            configReader = readServiceFactory.createReader(bootstrapConfig!!)
            val lister = ConfigListener { changedKeys: Set<String>, currentConfigurationSnapshot: Map<String, SmartConfig> ->
                if (!receivedSnapshot) {
                    if (changedKeys.contains(MESSAGING_CONFIG)) {
                        log.info("Config read service config snapshot received")
                        receivedSnapshot = true
                        lifeCycleCoordinator.postEvent(ConfigReceivedEvent(currentConfigurationSnapshot))
                    }
                } else {
                    log.info("Config read service config update received")
                    if (changedKeys.contains(MESSAGING_CONFIG)) {
                        log.info("Config update contains kafka config")
                        lifeCycleCoordinator.postEvent(MessagingConfigUpdateEvent(currentConfigurationSnapshot))
                    }
                }

            }
            sub = configReader!!.registerCallback(lister)
            configReader!!.start()
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
