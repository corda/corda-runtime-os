package net.corda.permissions.storage.reader

import net.corda.libs.configuration.SmartConfig
import net.corda.libs.permissions.storage.reader.PermissionStorageReader
import net.corda.libs.permissions.storage.reader.factory.PermissionStorageReaderFactory
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.permissions.cache.PermissionCacheService
import net.corda.permissions.storage.reader.internal.PermissionStorageReaderServiceEventHandler
import javax.persistence.EntityManagerFactory

@Suppress("LongParameterList")
class PermissionStorageReaderService(
    permissionCacheService: PermissionCacheService,
    permissionStorageReaderFactory: PermissionStorageReaderFactory,
    coordinatorFactory: LifecycleCoordinatorFactory,
    entityManagerFactory: EntityManagerFactory,
    publisherFactory: PublisherFactory,
    bootstrapConfig: SmartConfig
) : Lifecycle {

    val permissionStorageReader: PermissionStorageReader? get() = handler.permissionStorageReader

    private val handler = PermissionStorageReaderServiceEventHandler(
        permissionCacheService,
        permissionStorageReaderFactory,
        entityManagerFactory,
        publisherFactory,
        bootstrapConfig
    )

    private val coordinator = coordinatorFactory.createCoordinator<PermissionStorageReaderService>(handler)

    override val isRunning: Boolean get() = coordinator.isRunning

    override fun start() {
        coordinator.postEvent(StartEvent())
    }

    override fun stop() {
        coordinator.postEvent(StopEvent())
    }
}