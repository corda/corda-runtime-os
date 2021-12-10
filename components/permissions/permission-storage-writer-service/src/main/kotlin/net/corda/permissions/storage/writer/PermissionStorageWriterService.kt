package net.corda.permissions.storage.writer

import net.corda.libs.configuration.SmartConfig
import net.corda.libs.permissions.storage.writer.factory.PermissionStorageWriterProcessorFactory
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.permissions.storage.reader.PermissionStorageReaderService
import net.corda.permissions.storage.writer.internal.PermissionStorageWriterServiceEventHandler
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import javax.persistence.EntityManagerFactory

@Component(service = [PermissionStorageWriterService::class])
class PermissionStorageWriterService @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = EntityManagerFactory::class)
    entityManagerFactory: EntityManagerFactory,
    @Reference(service = SubscriptionFactory::class)
    subscriptionFactory: SubscriptionFactory,
    @Reference(service = PermissionStorageWriterProcessorFactory::class)
    permissionStorageWriterProcessorFactory: PermissionStorageWriterProcessorFactory,
    @Reference(service = SmartConfig::class)
    private val nodeConfig: SmartConfig,
    @Reference(service = PermissionStorageReaderService::class)
    private val readerService: PermissionStorageReaderService
) : Lifecycle {

    private val coordinator = coordinatorFactory.createCoordinator<PermissionStorageWriterService>(
        PermissionStorageWriterServiceEventHandler(
            entityManagerFactory,
            subscriptionFactory,
            permissionStorageWriterProcessorFactory,
            nodeConfig,
            readerService
        )
    ).also {
        it.followStatusChangesByName(setOf(LifecycleCoordinatorName.forComponent<PermissionStorageReaderService>()))
    }

    override val isRunning: Boolean get() = coordinator.isRunning

    override fun start() {
        coordinator.postEvent(StartEvent())
    }

    override fun stop() {
        coordinator.postEvent(StopEvent())
    }
}