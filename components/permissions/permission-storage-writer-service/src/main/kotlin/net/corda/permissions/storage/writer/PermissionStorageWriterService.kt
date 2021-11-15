package net.corda.permissions.storage.writer

import net.corda.libs.permissions.storage.writer.factory.PermissionStorageWriterFactory
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import javax.persistence.EntityManagerFactory

class PermissionStorageWriterService(
    coordinatorFactory: LifecycleCoordinatorFactory,
    entityManagerFactory: EntityManagerFactory,
    permissionStorageWriterFactory: PermissionStorageWriterFactory
) : Lifecycle {

    private val coordinator = coordinatorFactory.createCoordinator<PermissionStorageWriterService>(
        PermissionStorageWriterServiceEventHandler(
            entityManagerFactory,
            permissionStorageWriterFactory
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