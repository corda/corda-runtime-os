package net.corda.processors.db.internal.config.writeservice

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.processors.db.internal.db.DBWriter
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("Unused")
@Component(service = [ConfigWriteService::class])
class ConfigWriteServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = DBWriter::class)
    private val dbWriter: DBWriter,
    @Reference(service = SubscriptionFactory::class)
    private val subscriptionFactory: SubscriptionFactory
) : ConfigWriteService {
    private val eventHandler = ConfigWriteServiceEventHandler(dbWriter, subscriptionFactory)
    private val coordinator = coordinatorFactory.createCoordinator<ConfigWriteService>(eventHandler)

    override fun bootstrapConfig(config: SmartConfig, instanceId: Int) =
        coordinator.postEvent(BootstrapConfigEvent(config, instanceId))

    override val isRunning get() = coordinator.isRunning

    override fun start() = coordinator.start()

    override fun stop() = coordinator.stop()
}