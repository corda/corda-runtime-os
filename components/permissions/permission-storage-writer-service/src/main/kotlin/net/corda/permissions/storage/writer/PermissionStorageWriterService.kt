package net.corda.permissions.storage.writer

import net.corda.libs.configuration.SmartConfig
import net.corda.libs.permissions.storage.writer.factory.PermissionStorageWriterProcessorFactory
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import javax.persistence.EntityManagerFactory

class PermissionStorageWriterService(
    coordinatorFactory: LifecycleCoordinatorFactory,
    entityManagerFactory: EntityManagerFactory,
    subscriptionFactory: SubscriptionFactory,
    permissionStorageWriterProcessorFactory: PermissionStorageWriterProcessorFactory,
    nodeConfig: SmartConfig
) : Lifecycle {

    private val coordinator = coordinatorFactory.createCoordinator<PermissionStorageWriterService>(
        PermissionStorageWriterServiceEventHandler(
            entityManagerFactory,
            subscriptionFactory,
            permissionStorageWriterProcessorFactory,
            nodeConfig
        )
    )

    override val isRunning: Boolean get() = coordinator.isRunning

    override fun start() {
        coordinator.postEvent(StartEvent())
    }

    override fun stop() {
        coordinator.postEvent(StopEvent())
    }
}