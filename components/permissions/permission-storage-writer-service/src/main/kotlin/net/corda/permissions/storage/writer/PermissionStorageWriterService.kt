package net.corda.permissions.storage.writer

import net.corda.configuration.read.ConfigurationReadService
import net.corda.db.schema.DbSchema
import net.corda.libs.permissions.storage.writer.factory.PermissionStorageWriterProcessorFactory
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.orm.EntitiesSet
import net.corda.orm.EntityManagerFactoryFactory
import net.corda.permissions.storage.reader.PermissionStorageReaderService
import net.corda.permissions.storage.writer.internal.PermissionStorageWriterServiceEventHandler
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("LongParameterList")
@Component(service = [PermissionStorageWriterService::class])
class PermissionStorageWriterService @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = SubscriptionFactory::class)
    subscriptionFactory: SubscriptionFactory,
    @Reference(service = PermissionStorageWriterProcessorFactory::class)
    permissionStorageWriterProcessorFactory: PermissionStorageWriterProcessorFactory,
    @Reference(service = PermissionStorageReaderService::class)
    readerService: PermissionStorageReaderService,
    @Reference(service = ConfigurationReadService::class)
    configurationReadService: ConfigurationReadService,
    @Reference(service = EntityManagerFactoryFactory::class)
    entityManagerFactoryFactory: EntityManagerFactoryFactory,
    @Reference(service = EntitiesSet::class, name = DbSchema.RPC_RBAC)
    rbacEntitiesSet: EntitiesSet
) : Lifecycle {

    private val coordinator = coordinatorFactory.createCoordinator<PermissionStorageWriterService>(
        PermissionStorageWriterServiceEventHandler(
            subscriptionFactory,
            permissionStorageWriterProcessorFactory,
            readerService,
            configurationReadService,
            entityManagerFactoryFactory,
            rbacEntitiesSet
        )
    ).also {
        it.followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<PermissionStorageReaderService>(),
                LifecycleCoordinatorName.forComponent<ConfigurationReadService>()
            )
        )
    }

    override val isRunning: Boolean get() = coordinator.isRunning

    override fun start() {
        coordinator.postEvent(StartEvent())
    }

    override fun stop() {
        coordinator.postEvent(StopEvent())
    }
}