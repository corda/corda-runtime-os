package net.corda.processors.test.internal

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.DependentComponents
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.processors.test.TestProcessor
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger

@Suppress("LongParameterList", "Unused")
@Component(service = [TestProcessor::class])
class TestProcessorImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = SubscriptionFactory::class)
    subscriptionFactory: SubscriptionFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService
) : TestProcessor {

    private companion object {
        val log: Logger = contextLogger()
    }

    private val dependentComponents = DependentComponents.of(
        ::configurationReadService
    )

    private val lifecycleCoordinator = coordinatorFactory.createCoordinator<TestProcessorImpl>(::eventHandler)
    override fun start(bootConfig: SmartConfig) {
        log.info("Test processor starting.")
        lifecycleCoordinator.start()
        lifecycleCoordinator.postEvent(BootConfigEvent(bootConfig))
    }

    override fun stop() {
        log.info("Test processor stopping.")
        lifecycleCoordinator.stop()
    }

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        log.debug { "Flow processor received event $event." }

        when (event) {
            is StartEvent -> {
                dependentComponents.registerAndStartAll(coordinator)
            }
            is RegistrationStatusChangeEvent -> {
                log.info("Test processor is ${event.status}")
                coordinator.updateStatus(event.status)
            }
            is BootConfigEvent -> {
                configurationReadService.bootstrapConfig(event.config)
            }
            is ConfigChangedEvent -> {}
            is StopEvent -> {
                dependentComponents.stopAll()
                coordinator.updateStatus(LifecycleStatus.UP)
            }
            else -> {
                log.error("Unexpected event $event!")
            }
        }
    }
}

data class BootConfigEvent(val config: SmartConfig) : LifecycleEvent
