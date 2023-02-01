package net.corda.cpiinfo.read.fake

import net.corda.cpiinfo.read.CpiInfoListener
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.lifecycle.ErrorEvent
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.createCoordinator
import net.corda.reconciliation.VersionedRecord
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.propertytypes.ServiceRanking
import org.slf4j.LoggerFactory
import java.util.stream.Stream

@ServiceRanking(Int.MAX_VALUE)
@Component(service = [CpiInfoReadService::class, CpiInfoReadServiceFake::class])
class CpiInfoReadServiceFake internal constructor(
    cpiMetadatas: Iterable<CpiMetadata>,
    callbacks: Iterable<CpiInfoListener>,
    coordinatorFactory: LifecycleCoordinatorFactory,
) : CpiInfoReadService {

    @Activate
    constructor(
        @Reference(service = LifecycleCoordinatorFactory::class)
        coordinatorFactory: LifecycleCoordinatorFactory,
    ) : this(emptyList(), emptyList(), coordinatorFactory)

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val cpiData = cpiMetadatas.associateBy { it.cpiId }.toMutableMap()
    private val callbacks = callbacks.toMutableList()

    private val coordinator = coordinatorFactory.createCoordinator<CpiInfoReadService> { ev, coor ->
        when (ev) {
            is StartEvent -> logger.info("StartEvent received.")
            is StopEvent -> logger.info("StopEvent received.")
            is ErrorEvent -> logger.info("ErrorEvent ${ev.cause.message}")
            is RegistrationStatusChangeEvent ->
                if (ev.status in listOf(LifecycleStatus.UP, LifecycleStatus.DOWN))
                    coor.updateStatus(ev.status)
            else -> logger.info("Other event received $ev")
        }
    }

    fun addOrUpdate(cpiMetadata: CpiMetadata) {
        // Is this the proper implementation for update?
        // Combining the cpks or replacing the whole list?
        val cpi = get(cpiMetadata.cpiId)
        if (cpi == null) {
            cpiData[cpiMetadata.cpiId] = cpiMetadata
        } else {
            val combined = cpi.copy(cpksMetadata = cpi.cpksMetadata + cpiMetadata.cpksMetadata)
            cpiData[combined.cpiId] = combined
        }
        callbacks.forEach { it.onUpdate(setOf(cpiMetadata.cpiId), cpiData) }
    }

    fun remove(cpiIdentifier: CpiIdentifier) {
        cpiData.remove(cpiIdentifier)
        callbacks.forEach { it.onUpdate(setOf(cpiIdentifier), cpiData) }
    }

    fun reset() {
        cpiData.clear()
        callbacks.clear()
    }

    override fun getAll(): List<CpiMetadata> {
        throwIfNotRunning()
        return cpiData.values.toList()
    }

    override fun get(identifier: CpiIdentifier): CpiMetadata? {
        throwIfNotRunning()
        return cpiData[identifier]
    }

    override fun getAllVersionedRecords(): Stream<VersionedRecord<CpiIdentifier, CpiMetadata>>? {
        TODO("Not yet implemented")
    }

    override val lifecycleCoordinatorName: LifecycleCoordinatorName
        get() = coordinator.name

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    fun waitUntilRunning() {
        repeat(10) {
            if (isRunning) return
            Thread.sleep(100)
        }
        check(false) { "Timeout waiting for ${this::class.simpleName} to start" }
    }

    override fun stop() {
        coordinator.stop()
    }

    private fun throwIfNotRunning() {
        val reallyRunning = isRunning
        check(reallyRunning) { "${this::class.simpleName} has not been started." }
    }
}
