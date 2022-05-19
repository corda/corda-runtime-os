package net.corda.cpiinfo.read.impl

import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpiinfo.read.CpiInfoListener
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.reconciliation.VersionedRecord
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.virtualnode.common.ConfigChangedEvent
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import java.util.stream.Stream

/**
 * CPI Info Service Component which implements [CpiInfoReadService]
 *
 * You should register the callback in [CpiInfoReadService] if you want to be kept
 * up to date with changes, otherwise use the `get` method on [CpiInfoReadService] to get
 * the relevant meta data.
 */
@Component(service = [CpiInfoReadService::class])
class CpiInfoReadServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = SubscriptionFactory::class)
    subscriptionFactory: SubscriptionFactory
) : CpiInfoReadService {
    companion object {
        val log: Logger = contextLogger()
    }

    private val cpiInfoProcessor = CpiInfoReaderProcessor(::setStatusToUp, ::setStatusToError)

    /**
     * The event handler needs to call back to here and use this coordinator, we do NOT want to pass around
     * the coordinator.
     */
    private val eventHandler: CpiInfoReaderEventHandler = CpiInfoReaderEventHandler(
        configurationReadService,
        cpiInfoProcessor,
        subscriptionFactory,
        this::onConfigChangeEvent
    )

    override val lifecycleCoordinatorName = LifecycleCoordinatorName.forComponent<CpiInfoReadService>()

    private val coordinator = coordinatorFactory.createCoordinator(lifecycleCoordinatorName, eventHandler)

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
        log.debug { "Cpi Info Reader Service component starting" }
        coordinator.start()
    }

    override fun stop() {
        log.debug { "Cpi Info Reader Service component stopping" }
        coordinator.stop()
    }

    override fun close() {
        log.debug { "Cpi Info Reader Service component closing" }
        coordinator.close()
        cpiInfoProcessor.close()
    }

    override fun getAll(): List<CpiMetadata> = cpiInfoProcessor.getAll()

    override fun getAllVersionedRecords(): Stream<VersionedRecord<CpiIdentifier, CpiMetadata>> =
        getAll()
            .stream()
            .map {
                VersionedRecord(
                    version = it.version,
                    isDeleted = false,
                    key = it.cpiId,
                    value = it
                )
            }

    override fun get(identifier: CpiIdentifier): CpiMetadata? = cpiInfoProcessor.get(identifier)

    override fun registerCallback(listener: CpiInfoListener): AutoCloseable =
        cpiInfoProcessor.registerCallback(listener)
}
