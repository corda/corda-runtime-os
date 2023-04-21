package net.corda.cpi.upload.endpoints.service.impl

import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpi.upload.endpoints.service.CpiUploadService
import net.corda.libs.cpiupload.CpiUploadManager
import net.corda.libs.cpiupload.CpiUploadManagerFactory
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * This service is used for creating a new [CpiUploadManager] on new configuration through [CpiUploadServiceHandler].
 */
@Component(service = [CpiUploadService::class])
class CpiUploadServiceImpl @Activate constructor(
    @Reference(service = ConfigurationReadService::class)
    configReadService: ConfigurationReadService,
    @Reference(service = LifecycleCoordinatorFactory::class)
    coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = PublisherFactory::class)
    publisherFactory: PublisherFactory,
    @Reference(service = CpiUploadManagerFactory::class)
    cpiUploadManagerFactory: CpiUploadManagerFactory,
    @Reference(service = SubscriptionFactory::class)
    subscriptionFactory: SubscriptionFactory
) : CpiUploadService {

    private val handler = CpiUploadServiceHandler(
        cpiUploadManagerFactory,
        configReadService,
        publisherFactory,
        subscriptionFactory
    )

    private val coordinator: LifecycleCoordinator = coordinatorFactory.createCoordinator<CpiUploadService>(
        handler
    )

    override val cpiUploadManager: CpiUploadManager
        get() {
            checkNotNull(handler.cpiUploadManager) {
                "Cpi Upload Manager is null. Getter should be called only after service is UP."
            }
            return handler.cpiUploadManager!!
        }

    override val isRunning get() = coordinator.isRunning

    override fun start() = coordinator.start()

    override fun stop() = coordinator.stop()
}