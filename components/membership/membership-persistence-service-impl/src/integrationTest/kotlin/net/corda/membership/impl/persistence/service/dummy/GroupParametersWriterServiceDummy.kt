package net.corda.membership.impl.persistence.service.dummy

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.membership.groupparams.writer.service.GroupParametersWriterService
import net.corda.membership.lib.InternalGroupParameters
import net.corda.virtualnode.HoldingIdentity
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.propertytypes.ServiceRanking

@ServiceRanking(Int.MAX_VALUE)
@Component(service = [GroupParametersWriterService::class])
class GroupParametersWriterServiceDummy @Activate constructor(
    @Reference(service = LifecycleCoordinatorFactory::class)
    private val coordinatorFactory: LifecycleCoordinatorFactory,
) : GroupParametersWriterService {
    private val coordinator = coordinatorFactory.createCoordinator(lifecycleCoordinatorName) { event, coordinator ->
        if (event is StartEvent) {
            coordinator.updateStatus(LifecycleStatus.UP)
        }
    }
    override fun put(recordKey: HoldingIdentity, recordValue: InternalGroupParameters) {
        // Do nothing
    }

    override fun remove(recordKey: HoldingIdentity) {
        // Do nothing
    }

    override val lifecycleCoordinatorName
        get() = LifecycleCoordinatorName.forComponent<GroupParametersWriterServiceDummy>()

    override val isRunning: Boolean
        get() = coordinator.isRunning

    override fun start() {
        coordinator.start()
    }

    override fun stop() {
        coordinator.stop()
    }
}
