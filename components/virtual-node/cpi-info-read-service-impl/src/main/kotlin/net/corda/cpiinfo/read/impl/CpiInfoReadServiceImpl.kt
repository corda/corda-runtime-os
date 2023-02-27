package net.corda.cpiinfo.read.impl

import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.reconciliation.VersionedRecord
import net.corda.utilities.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.stream.Stream

/**
 * CPI Info Service Component which implements [CpiInfoReadService]
 *
 * You should register the callback in [CpiInfoReadService] if you want to be kept
 * up to date with changes, otherwise use the `get` method on [CpiInfoReadService] to get
 * the relevant meta data.
 */
@Suppress("Unused")
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
        private val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val cpiInfoProcessor = CpiInfoReaderProcessor(::setStatusToUp, ::setStatusToError)

    private val eventHandler: CpiInfoReaderEventHandler = CpiInfoReaderEventHandler(
        configurationReadService,
        cpiInfoProcessor,
        subscriptionFactory
    )

    override val lifecycleCoordinatorName = LifecycleCoordinatorName.forComponent<CpiInfoReadService>()

    private val coordinator = coordinatorFactory.createCoordinator(lifecycleCoordinatorName, eventHandler)

    /** The processor calls this method on snapshot, and it updates the status of the coordinator. */
    private fun setStatusToUp() = coordinator.updateStatus(LifecycleStatus.UP)

    /** The processor calls this method on snapshot, and it updates the status of the coordinator. */
    private fun setStatusToError() = coordinator.updateStatus(LifecycleStatus.ERROR)

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

    @Deactivate
    fun close() {
        log.debug { "Cpi Info Reader Service component closing" }
        coordinator.close()
    }

    override fun getAll(): Collection<CpiMetadata> = cpiInfoProcessor.getAll()

    override fun getAllVersionedRecords(): Stream<VersionedRecord<CpiIdentifier, CpiMetadata>> =
        getAll()
            .stream()
            .map {
                object : VersionedRecord<CpiIdentifier, CpiMetadata> {
                    override val version = it.version
                    override val isDeleted = false
                    override val key = it.cpiId
                    override val value = it
                }
            }

    override fun get(identifier: CpiIdentifier): CpiMetadata? = cpiInfoProcessor.get(identifier)
}
