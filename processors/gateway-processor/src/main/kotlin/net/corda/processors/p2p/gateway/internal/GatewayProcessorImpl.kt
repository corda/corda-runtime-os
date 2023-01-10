package net.corda.processors.p2p.gateway.internal

import net.corda.configuration.read.ConfigurationReadService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.merger.ConfigMerger
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.p2p.gateway.Gateway
import net.corda.processors.p2p.gateway.GatewayProcessor
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("LongParameterList", "Unused")
@Component(service = [GatewayProcessor::class])
class GatewayProcessorImpl @Activate constructor(
    @Reference(service = ConfigMerger::class)
    private val configMerger: ConfigMerger,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory,
    @Reference(service = CryptoOpsClient::class)
    private val cryptoOpsClient: CryptoOpsClient,
    @Reference(service = AvroSchemaRegistry::class)
    private val avroSchemaRegistry: AvroSchemaRegistry
) : GatewayProcessor {

    private companion object {
        val logger = contextLogger()
    }

    private val lifecycleCoordinator = coordinatorFactory.createCoordinator<GatewayProcessorImpl>(::eventHandler)

    private var gateway: Gateway? = null
    private var registration: RegistrationHandle? = null

    override fun start(bootConfig: SmartConfig) {
        logger.info("Gateway processor starting.")
        lifecycleCoordinator.start()
        lifecycleCoordinator.postEvent(BootConfigEvent(bootConfig))
    }

    override fun stop() {
        logger.info("Gateway processor stopping.")
        lifecycleCoordinator.stop()
    }

    private fun eventHandler(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> {
                configurationReadService.start()
            }
            is RegistrationStatusChangeEvent -> {
                logger.info("Gateway processor is ${event.status}")
                coordinator.updateStatus(event.status)
            }
            is BootConfigEvent -> {
                configurationReadService.bootstrapConfig(event.config)

                val gateway = Gateway(
                    configurationReadService,
                    subscriptionFactory,
                    publisherFactory,
                    coordinatorFactory,
                    configMerger.getMessagingConfig(event.config),
                    cryptoOpsClient,
                    avroSchemaRegistry
                )
                this.gateway = gateway

                registration?.close()
                registration = lifecycleCoordinator.followStatusChangesByName(
                    setOf(
                        LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
                        gateway.dominoTile.coordinatorName
                    )
                )

                gateway.start()
            }
            is StopEvent -> {
                gateway?.stop()
            }
        }
    }

    data class BootConfigEvent(val config: SmartConfig) : LifecycleEvent
}