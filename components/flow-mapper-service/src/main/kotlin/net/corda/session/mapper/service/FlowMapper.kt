package net.corda.session.mapper.service

import net.corda.configuration.read.ConfigurationReadService
import net.corda.flow.mapper.factory.FlowMapperEventExecutorFactory
import net.corda.flow.mapper.factory.FlowMapperMetaDataFactory
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
import net.corda.schema.configuration.ConfigKeys.Companion.BOOT_CONFIG
import net.corda.schema.configuration.ConfigKeys.Companion.FLOW_CONFIG
import net.corda.schema.configuration.ConfigKeys.Companion.MESSAGING_CONFIG
import net.corda.session.mapper.service.executor.FlowMapperService
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("LongParameterList")
@Component(service = [FlowMapperComponent::class])
class FlowMapperComponent @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = FlowMapperMetaDataFactory::class)
    private val flowMetaDataFactory: FlowMapperMetaDataFactory,
    @Reference(service = FlowMapperEventExecutorFactory::class)
    private val flowMapperEventExecutorFactory: FlowMapperEventExecutorFactory,
) : Lifecycle {

    private companion object {
        private val logger = contextLogger()
    }

    private val coordinator = coordinatorFactory.createCoordinator<FlowMapperComponent>(::eventHandler)
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
                flowMapperService = FlowMapperService(
                    coordinatorFactory,
                    subscriptionFactory,
                    publisherFactory,
                    event.config,
                    flowMetaDataFactory,
                    flowMapperEventExecutorFactory
                )
                flowMapperService?.start()
            }
            is StopEvent -> {
                logger.info("Stopping flow mapper component.")
                flowMapperService?.close()
                flowMapperService = null
                registration?.close()
                registration = null
            }
        }
    }

    private fun onConfigChange(keys: Set<String>, config: Map<String, SmartConfig>) {
        if (isRelevantConfigKey(keys)) {
            coordinator.postEvent(
                NewConfigurationReceived(
                    config[BOOT_CONFIG]!!.withFallback(config[MESSAGING_CONFIG]).withFallback
                        (config[FLOW_CONFIG])
                )
            )
        }
    }

    /**
     * True if any of the config [keys] are relevant to this app.
     */
    private fun isRelevantConfigKey(keys: Set<String>): Boolean {
        return MESSAGING_CONFIG in keys || BOOT_CONFIG in keys || FLOW_CONFIG in keys
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
