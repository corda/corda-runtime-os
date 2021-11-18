package net.corda.permissions.storage.reader

import net.corda.libs.permissions.storage.reader.factory.PermissionStorageReaderFactory
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.permissions.cache.PermissionCacheService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import javax.persistence.EntityManagerFactory

@Component(service = [PermissionStorageReaderService::class])
class PermissionStorageReaderService @Activate constructor(
    @Reference(service = PermissionCacheService::class)
    permissionCacheService: PermissionCacheService,
    @Reference(service = PermissionStorageReaderFactory::class)
    permissionStorageReaderFactory: PermissionStorageReaderFactory,
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = EntityManagerFactory::class)
    entityManagerFactory: EntityManagerFactory,
    @Reference(service = PublisherFactory::class)
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