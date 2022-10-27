package net.corda.permissions.storage.reader

import net.corda.configuration.read.ConfigurationReadService
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.libs.permissions.storage.reader.PermissionStorageReader
import net.corda.libs.permissions.storage.reader.factory.PermissionStorageReaderFactory
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.permissions.management.cache.PermissionManagementCacheService
import net.corda.permissions.validation.cache.PermissionValidationCacheService
import net.corda.permissions.storage.reader.internal.PermissionStorageReaderServiceEventHandler
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("LongParameterList")
@Component(service = [PermissionStorageReaderService::class])
class PermissionStorageReaderService @Activate constructor(
    @Reference(service = PermissionValidationCacheService::class)
    permissionValidationCacheService: PermissionValidationCacheService,
    @Reference(service = PermissionManagementCacheService::class)
    permissionManagementCacheService: PermissionManagementCacheService,
    @Reference(service = PermissionStorageReaderFactory::class)
    permissionStorageReaderFactory: PermissionStorageReaderFactory,
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PublisherFactory::class)
    publisherFactory: PublisherFactory,
    @Reference(service = ConfigurationReadService::class)
    configurationReadService: ConfigurationReadService,
    @Reference(service = DbConnectionManager::class)
    dbConnectionManager: DbConnectionManager,
) : Lifecycle {

    private companion object {
        private val logger = contextLogger()
    }

    val permissionStorageReader: PermissionStorageReader? get() = handler.permissionStorageReader

    private val handler = PermissionStorageReaderServiceEventHandler(
        permissionValidationCacheService,
        permissionManagementCacheService,
        permissionStorageReaderFactory,
        publisherFactory,
        configurationReadService,
    ) { dbConnectionManager.getOrCreateEntityManagerFactory(CordaDb.RBAC, DbPrivilege.DML) }

    private val coordinator = coordinatorFactory.createCoordinator<PermissionStorageReaderService>(handler)

    override val isRunning: Boolean get() = coordinator.isRunning

    override fun start() {
        coordinator.postEvent(StartEvent())
    }

    override fun stop() {
        logger.warn("stopping ${coordinator.name} due to stop being called")
        coordinator.postEvent(StopEvent())
    }
}