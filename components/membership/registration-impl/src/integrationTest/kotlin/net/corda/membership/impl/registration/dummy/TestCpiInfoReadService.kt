package net.corda.membership.impl.registration.dummy

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.crypto.core.SecureHashImpl
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.reconciliation.VersionedRecord
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.propertytypes.ServiceRanking
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.stream.Stream

interface TestCpiInfoReadService : CpiInfoReadService {
    fun loadGroupPolicy(groupPolicy: String)
}

@ServiceRanking(Int.MAX_VALUE)
@Component(service = [CpiInfoReadService::class, TestCpiInfoReadService::class])
class TestCpiInfoReadServiceImpl @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
) : TestCpiInfoReadService {
    private companion object {
        const val UNIMPLEMENTED_FUNCTION = "Called unimplemented function for test service."
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private var cachedGroupPolicy: String? = null

    private val coordinator = coordinatorFactory.createCoordinator(
        LifecycleCoordinatorName.forComponent<CpiInfoReadService>(),
    ) { event, coordinator ->
        if (event is StartEvent) {
            coordinator.updateStatus(LifecycleStatus.UP)
        }
    }

    override fun loadGroupPolicy(groupPolicy: String) {
        cachedGroupPolicy = groupPolicy
    }

    override fun getAll(): Collection<CpiMetadata> {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override fun get(identifier: CpiIdentifier): CpiMetadata {
        return CpiMetadata(
            identifier,
            SecureHashImpl("SHA-256", byteArrayOf(1)),
            emptySet(),
            cachedGroupPolicy,
            1,
            Instant.now(),
        )
    }

    override fun getAllVersionedRecords(): Stream<VersionedRecord<CpiIdentifier, CpiMetadata>> {
        with(UNIMPLEMENTED_FUNCTION) {
            logger.warn(this)
            throw UnsupportedOperationException(this)
        }
    }

    override val lifecycleCoordinatorName: LifecycleCoordinatorName
        get() = LifecycleCoordinatorName.forComponent<CpiInfoReadService>()

    override val isRunning: Boolean
        get() = coordinator.status == LifecycleStatus.UP

    override fun start() {
        logger.info("TestCpiInfoReadService starting.")
        coordinator.start()
    }

    override fun stop() {
        logger.info("TestCpiInfoReadService stopping.")
        coordinator.stop()
    }
}
