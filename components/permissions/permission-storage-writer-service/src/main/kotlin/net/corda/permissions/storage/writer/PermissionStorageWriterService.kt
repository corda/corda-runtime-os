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
import javax.persistence.EntityManagerFactory

@Suppress("LongParameterList")
class PermissionStorageWriterService(
    coordinatorFactory: LifecycleCoordinatorFactory,
    entityManagerFactory: EntityManagerFactory,
    subscriptionFactory: SubscriptionFactory,
    permissionStorageWriterProcessorFactory: PermissionStorageWriterProcessorFactory,
    bootstrapConfig: SmartConfig,
    readerService: PermissionStorageReaderService
) : Lifecycle {

    private val coordinator = coordinatorFactory.createCoordinator<PermissionStorageWriterService>(
        PermissionStorageWriterServiceEventHandler(
            entityManagerFactory,
            subscriptionFactory,
            permissionStorageWriterProcessorFactory,
            bootstrapConfig,
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