package net.corda.permissions.storage.writer

import net.corda.configuration.read.ConfigurationReadService
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

@Suppress("LongParameterList")
class PermissionStorageWriterService(
    coordinatorFactory: LifecycleCoordinatorFactory,
    subscriptionFactory: SubscriptionFactory,
    permissionStorageWriterProcessorFactory: PermissionStorageWriterProcessorFactory,
    readerService: PermissionStorageReaderService,
    configurationReadService: ConfigurationReadService,
    entityManagerFactoryFactory: EntityManagerFactoryFactory,
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