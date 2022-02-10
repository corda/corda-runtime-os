package net.corda.permissions.storage.writer

import net.corda.configuration.read.ConfigurationReadService
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
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
    @Reference(service = DbConnectionManager::class)
    private val dbConnectionManager: DbConnectionManager,
) : Lifecycle {

    private val coordinator = coordinatorFactory.createCoordinator<PermissionStorageWriterService>(
        PermissionStorageWriterServiceEventHandler(
            subscriptionFactory,
            permissionStorageWriterProcessorFactory,
            readerService,
            configurationReadService
        ) { dbConnectionManager.getOrCreateEntityManagerFactory(CordaDb.RBAC, DbPrivilege.DML) }
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