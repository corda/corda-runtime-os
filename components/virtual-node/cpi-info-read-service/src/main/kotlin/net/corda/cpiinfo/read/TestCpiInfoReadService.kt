package net.corda.cpiinfo.read

import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.createCoordinator
import net.corda.reconciliation.VersionedRecord
import net.corda.v5.base.util.contextLogger
import java.util.stream.Stream

class TestCpiInfoReadService(
    coordinatorFactory: LifecycleCoordinatorFactory
) : CpiInfoReadService {

    private val lifecycleCoordinator = coordinatorFactory.createCoordinator<CpiInfoReadService>{ event, coordinator ->
        if(event is StartEvent) { coordinator.updateStatus(LifecycleStatus.UP) }
    }

    companion object {
        val logger = contextLogger()
        private const val UNIMPLEMENTED_FUNCTION = "Called unimplemented function for test service"
    }

    override fun getAll(): Collection<CpiMetadata> {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override fun get(identifier: CpiIdentifier): CpiMetadata? {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override fun getAllVersionedRecords(): Stream<VersionedRecord<CpiIdentifier, CpiMetadata>>? {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override val lifecycleCoordinatorName: LifecycleCoordinatorName
        get() = lifecycleCoordinator.name

    override val isRunning: Boolean
        get() = lifecycleCoordinator.isRunning

    override fun start() {
        lifecycleCoordinator.start()
    }

    override fun stop() {
        lifecycleCoordinator.stop()
    }
}