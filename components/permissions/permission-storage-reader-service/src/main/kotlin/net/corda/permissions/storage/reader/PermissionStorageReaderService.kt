package net.corda.permissions.storage.reader

import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.permissions.storage.reader.PermissionStorageReader
import net.corda.libs.permissions.storage.reader.factory.PermissionStorageReaderFactory
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.orm.JpaEntitiesSet
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.permissions.cache.PermissionCacheService
import net.corda.permissions.storage.reader.internal.PermissionStorageReaderServiceEventHandler
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality
import org.osgi.service.component.annotations.ReferencePolicy

@Suppress("LongParameterList")
@Component(service = [PermissionStorageReaderService::class])
class PermissionStorageReaderService @Activate constructor(
    @Reference(service = PermissionCacheService::class)
    permissionCacheService: PermissionCacheService,
    @Reference(service = PermissionStorageReaderFactory::class)
    permissionStorageReaderFactory: PermissionStorageReaderFactory,
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = EntityManagerFactoryFactory::class)
    entityManagerFactoryFactory: EntityManagerFactoryFactory,
    @Reference(service = PublisherFactory::class)
    publisherFactory: PublisherFactory,
    @Reference(service = ConfigurationReadService::class)
    configurationReadService: ConfigurationReadService
) : Lifecycle {

    @Reference(
        service = JpaEntitiesSet::class,
        cardinality = ReferenceCardinality.MULTIPLE,
        policy = ReferencePolicy.DYNAMIC
    )
    private val allEntitiesSets: List<JpaEntitiesSet> = mutableListOf()

    val permissionStorageReader: PermissionStorageReader? get() = handler.permissionStorageReader

    private val handler = PermissionStorageReaderServiceEventHandler(
        permissionCacheService,
        permissionStorageReaderFactory,
        publisherFactory,
        configurationReadService,
        entityManagerFactoryFactory,
        allEntitiesSets
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