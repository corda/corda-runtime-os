package net.corda.membership.impl.persistence.service.dummy

import net.corda.data.p2p.mtls.AllowedCertificateSubject
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.membership.mtls.allowed.list.service.AllowedCertificatesReaderWriterService
import net.corda.reconciliation.VersionedRecord
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.propertytypes.ServiceRanking
import java.util.stream.Stream

@ServiceRanking(Int.MAX_VALUE)
@Component(service = [AllowedCertificatesReaderWriterService::class])
class TestAllowedCertificatesReaderWriterService @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
) : AllowedCertificatesReaderWriterService {
    override val lifecycleCoordinatorName
        get() = LifecycleCoordinatorName.forComponent<AllowedCertificatesReaderWriterService>()
    private val coordinator = coordinatorFactory.createCoordinator(lifecycleCoordinatorName) { event, coordinator ->
        if (event is StartEvent) {
            coordinator.updateStatus(LifecycleStatus.UP)
        }
    }

    override fun getAllVersionedRecords():
            Stream<VersionedRecord<AllowedCertificateSubject, AllowedCertificateSubject>>? {
        throw UnsupportedOperationException()
    }


    override fun put(recordKey: AllowedCertificateSubject, recordValue: AllowedCertificateSubject) {
        throw UnsupportedOperationException()
    }

    override fun remove(recordKey: AllowedCertificateSubject) {
        throw UnsupportedOperationException()
    }

    override val isRunning = true

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }
}