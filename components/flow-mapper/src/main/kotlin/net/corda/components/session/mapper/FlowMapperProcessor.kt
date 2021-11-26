package net.corda.components.session.mapper

import net.corda.components.session.mapper.service.FlowMapperService
import net.corda.configuration.read.ConfigKeys
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfig
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
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowMapperProcessor::class])
class FlowMapperProcessor @Activate constructor(
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
    }

    private val coordinator = coordinatorFactory.createCoordinator<FlowMapperProcessor>(::eventHandler)
    private var registration: RegistrationHandle? = null
    private var configHandle: AutoCloseable? = null
    private var flowMapperService: FlowMapperService? = null

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                logger.info("Starting flow mapper processor component.")
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
                logger.info("Flow mapper processor component configuration received")
                flowMapperService?.close()
                flowMapperService = FlowMapperService(coordinatorFactory, subscriptionFactory, publisherFactory, event.config)
                flowMapperService?.start()
            }
            is StopEvent -> {
                logger.info("Stopping flow mapper component.")
                flowMapperService?.close()
                registration?.close()
                registration = null
            }
        }
    }

    private fun onConfigChange(keys: Set<String>, config: Map<String, SmartConfig>) {
        if (isRelevantConfigKey(keys) && config.keys.containsAll(listOf(
                ConfigKeys.MESSAGING_KEY,
                ConfigKeys.BOOTSTRAP_KEY,
                ConfigKeys.FLOW_MAPPER_KEY
            ))) {
            coordinator.postEvent(
                NewConfigurationReceived(config[ConfigKeys.BOOTSTRAP_KEY]!!.withFallback(config[ConfigKeys.MESSAGING_KEY]).withFallback
                    (config[ConfigKeys.FLOW_MAPPER_KEY]))
            )
        }
    }

    /**
     * True if any of the config [keys] are relevant to this app.
     */
    private fun isRelevantConfigKey(keys: Set<String>) : Boolean {
        return ConfigKeys.MESSAGING_KEY in keys || ConfigKeys.BOOTSTRAP_KEY in keys || ConfigKeys.FLOW_MAPPER_KEY in keys
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

data class NewConfigurationReceived(val config: SmartConfig) : LifecycleEvent
