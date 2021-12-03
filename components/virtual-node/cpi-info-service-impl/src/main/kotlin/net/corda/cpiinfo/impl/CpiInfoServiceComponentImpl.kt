package net.corda.cpiinfo.impl

import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpiinfo.CpiInfoListener
import net.corda.cpiinfo.CpiInfoService
import net.corda.cpiinfo.CpiInfoServiceComponent
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.createCoordinator
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.packaging.CPI
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger

/**
 * CPI Info Service Component which implements [CpiInfoService]
 *
 * You should register the callback in [CpiInfoServiceComponent] if you want to be kept
 * up to date with changes, otherwise use the `get` method on [CpiInfoService] to get
 * the relevant meta data.
 */
@Component(service = [CpiInfoServiceComponent::class])
class CpiInfoServiceComponentImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = SubscriptionFactory::class)
    subscriptionFactory: SubscriptionFactory
) : CpiInfoServiceComponent {
    companion object {
        val log: Logger = contextLogger()
    }

    private val cpiInfoProcessor = CpiInfoProcessor(::setStatusToUp, ::setStatusToError)

    /**
     * The event handler needs to call back to here and use this coordinator, we do NOT want to pass around
     * the coordinator.
     */
    private val eventHandler: CpiInfoEventHandler = CpiInfoEventHandler(
        configurationReadService,
        cpiInfoProcessor,
        subscriptionFactory,
        this::onConfigChangeEvent
    )

    private val coordinator = coordinatorFactory.createCoordinator<CpiInfoService>(eventHandler)

    /** The processor calls this method on snapshot, and it updates the status of the coordinator. */
    private fun setStatusToUp() = coordinator.updateStatus(LifecycleStatus.UP)

    /** The processor calls this method on snapshot, and it updates the status of the coordinator. */
    private fun setStatusToError() = coordinator.updateStatus(LifecycleStatus.ERROR)

    /** Post a [ConfigChangedEvent]  */
    private fun onConfigChangeEvent(event: ConfigChangedEvent) = coordinator.postEvent(event)

    override val isRunning: Boolean
        get() {
            return coordinator.isRunning
        }

    override fun start() {
        log.debug { "Cpi Info Service component starting" }
        coordinator.start()
    }

    override fun stop() {
        log.debug { "Cpi Info Service component stopping" }
        coordinator.stop()
    }

    override fun close() {
        log.debug { "Cpi Info Service component closing" }
        coordinator.close()
        cpiInfoProcessor.close()
    }

    override fun get(identifier: CPI.Identifier): CPI.Metadata? = cpiInfoProcessor.get(identifier)

    override fun registerCallback(listener: CpiInfoListener): AutoCloseable =
        cpiInfoProcessor.registerCallback(listener)
}
