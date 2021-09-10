package net.corda.components.rpc

import com.typesafe.config.Config
import net.corda.libs.configuration.read.ConfigListener
import net.corda.libs.configuration.read.ConfigReadService
import net.corda.libs.configuration.read.factory.ConfigReadServiceFactory
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger


class ConfigReceivedEvent(val currentConfigurationSnapshot: Map<String, Config>) : LifecycleEvent
class MessagingConfigUpdateEvent(val currentConfigurationSnapshot: Map<String, Config>) : LifecycleEvent

class HttpRpcGateway(
    private val lifeCycleCoordinator: LifecycleCoordinator,
    @Reference(service = ConfigReadServiceFactory::class)
    private val readServiceFactory: ConfigReadServiceFactory
) : Lifecycle {

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
        log.info("Starting with bootstrap config")
        this.bootstrapConfig = bootstrapConfig
        this.start()
    }

    override fun start() {
        log.info("Starting from lifecycle event")
        if (bootstrapConfig == null) {
            val message = "Use the other start method available and pass in the bootstrap configuration"
            log.error(message)
            throw CordaRuntimeException(message)
        }
        configReadService = readServiceFactory.createReadService(bootstrapConfig!!)
        val listener = ConfigListener { changedKeys: Set<String>, currentConfigurationSnapshot: Map<String, Config> ->
            if (changedKeys.contains(MESSAGING_CONFIG)) {
                if (receivedSnapshot) {
                    log.info("Config update received")
                    log.info("Config update contains kafka config")
                    lifeCycleCoordinator.postEvent(MessagingConfigUpdateEvent(currentConfigurationSnapshot))
                } else {
                    receivedSnapshot = true
                    log.info("Config snapshot received")
                    lifeCycleCoordinator.postEvent(ConfigReceivedEvent(currentConfigurationSnapshot))
                }
            }
        }
        sub = configReadService!!.registerCallback(listener)
        configReadService!!.start()
    }

    override fun stop() {
        sub?.close()
        sub = null
    }
}