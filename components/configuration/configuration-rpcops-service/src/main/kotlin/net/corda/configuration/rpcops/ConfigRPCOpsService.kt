package net.corda.configuration.rpcops

import net.corda.configuration.read.ConfigurationReadService
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.lifecycle.registry.LifecycleRegistry
import net.corda.messaging.api.publisher.factory.PublisherFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

// TODO - Joel - Split between interface and implementation for this service. Rework exported packages.

// TODO - Joel - Describe.
@Component(service = [ConfigRPCOpsService::class])
class ConfigRPCOpsService @Activate constructor(
    @Reference(service = ConfigurationReadService::class)
    private val configReadService: ConfigurationReadService,
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
    @Reference(service = ConfigRPCOpsRPCSender::class)
    private val configRPCOpsRPCSender: ConfigRPCOpsRPCSender
) : Lifecycle {

    internal val coordinator = let {
        val eventHandler = ConfigRPCOpsEventHandler(configReadService, publisherFactory, configRPCOpsRPCSender, this)
        coordinatorFactory.createCoordinator<ConfigRPCOpsService>(eventHandler)
    }

    override val isRunning get() = coordinator.isRunning

    override fun start() = coordinator.start()

    override fun stop() = coordinator.stop()
}