package net.corda.configuration.read.file.impl

import com.typesafe.config.Config
import net.corda.configuration.read.ConfigurationHandler
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.read.ConfigReadService
import net.corda.libs.configuration.read.factory.ConfigReadServiceFactory
import net.corda.lifecycle.ErrorEvent
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [ConfigurationReadService::class])
class FileConfigurationReadServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigReadServiceFactory::class)
    private val configReadServiceFactory: ConfigReadServiceFactory
) : ConfigurationReadService {

    private companion object {
        val log = contextLogger()
    }

    private var bootstrapConfig: Config? = null

    private var subscription: ConfigReadService? = null

    private val callbackHandles = FileConfigurationHandlerStorage()

    @Volatile
    private var stopped = true

    override val isRunning: Boolean get() = !stopped

    private var lifecycleCoordinator: LifecycleCoordinator = coordinatorFactory.createCoordinator<ConfigurationReadService>(::eventHandler)

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                log.debug { "File configuration read service starting up." }
                if (bootstrapConfig != null) {
                    coordinator.postEvent(SetupSubscription)
                }
            }
            is BootstrapConfigProvided -> {
                // Let the lifecycle library error the service. The application can listen for error events and
                // respond accordingly.
                requireNotNull(bootstrapConfig) {
                    log.error("An attempt was made to set the bootstrap configuration twice.")
                    "An attempt was made to set the bootstrap configuration twice."
                }
                bootstrapConfig = event.config
                coordinator.postEvent(SetupSubscription)
            }
            is SetupSubscription -> {
                setupSubscription()
                coordinator.updateStatus(LifecycleStatus.UP, "Connected to configuration repository.")
            }
            is StopEvent -> {
                log.debug { "File configuration read service stopping." }
                callbackHandles.removeSubscription()
                subscription?.stop()
                subscription = null
            }
            is ErrorEvent -> log.error("An error occurred in the file configuration read service: ${event.cause.message}.", event.cause)
        }
    }

    private fun setupSubscription() {
        val config = requireNotNull(bootstrapConfig) { "Cannot setup the subscription with no bootstrap configuration" }
        if (subscription != null) {
            throw IllegalArgumentException("The subscription already exists")
        }
        val sub = configReadServiceFactory.createReadService(config)
        subscription = sub
        callbackHandles.addSubscription(sub)
        sub.start()
    }

    override fun bootstrapConfig(config: Config) {
        lifecycleCoordinator.postEvent(BootstrapConfigProvided(config))
    }

    override fun registerForUpdates(configHandler: ConfigurationHandler): AutoCloseable {
        return callbackHandles.add(configHandler)
    }

    override fun start() {
        lifecycleCoordinator.start()
    }

    override fun stop() {
        lifecycleCoordinator.stop()
    }
}

