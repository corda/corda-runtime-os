package net.corda.permissions.storage.reader

import net.corda.libs.permissions.storage.reader.factory.PermissionStorageReaderFactory
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.permissions.cache.PermissionCacheService
import javax.persistence.EntityManagerFactory

class PermissionStorageReaderService(
    permissionCacheService: PermissionCacheService,
    permissionStorageReaderFactory: PermissionStorageReaderFactory,
    coordinatorFactory: LifecycleCoordinatorFactory,
    entityManagerFactory: EntityManagerFactory,
    publisherFactory: PublisherFactory
) : Lifecycle {

    private val coordinator = coordinatorFactory.createCoordinator<PermissionStorageReaderService>(
        PermissionStorageReaderServiceEventHandler(
            permissionCacheService,
            permissionStorageReaderFactory,
            entityManagerFactory,
            publisherFactory
        )
    )

    override val isRunning: Boolean get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }
}