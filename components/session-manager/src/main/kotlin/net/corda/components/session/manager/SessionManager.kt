package net.corda.components.session.manager

import com.typesafe.config.Config
import net.corda.components.session.manager.dedup.DeduplicationManager
import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [SessionManager::class])
class SessionManager @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory
) : Lifecycle {

    private companion object {
        private val logger = contextLogger()

        private const val SESSION_KEY = "corda.session"
        private const val BOOT_KEY = "corda.boot"
        private const val MESSAGING_KEY = "corda.messaging"
    }

    private val coordinator = coordinatorFactory.createCoordinator<SessionManager>(::eventHandler)
    private var registration: RegistrationHandle? = null
    private var configHandle: AutoCloseable? = null
    private var deduplicationManager: DeduplicationManager? = null

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                logger.debug { "Starting flow runner component." }
                registration?.close()
                registration =
                    coordinator.followStatusChangesByName(
                        setOf(
                            LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
                        )
                    )
            }
            is RegistrationStatusChangeEvent -> {
                // No need to check what registration this is as there is only one.
                if (event.status == LifecycleStatus.UP) {
                    configHandle = configurationReadService.registerForUpdates(::onConfigChange)
                } else {
                    configHandle?.close()
                }
            }
            is NewConfigurationReceived -> {
                deduplicationManager = DeduplicationManager(coordinatorFactory, subscriptionFactory, publisherFactory, event.config)
            }
            is StopEvent -> {
                logger.debug { "Stopping flow runner component." }
                registration?.close()
                registration = null
            }
        }
    }

    private fun onConfigChange(keys: Set<String>, config: Map<String, Config>) {
        if (SESSION_KEY in config.keys && MESSAGING_KEY in config.keys && BOOT_KEY in config.keys) {
            coordinator.postEvent(NewConfigurationReceived(config))
        }
    }

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }
}

data class NewConfigurationReceived(val config: Map<String, Config>) : LifecycleEvent
