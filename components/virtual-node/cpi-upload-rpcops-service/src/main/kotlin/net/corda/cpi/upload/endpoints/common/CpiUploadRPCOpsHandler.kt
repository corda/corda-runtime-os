package net.corda.cpi.upload.endpoints.common

import net.corda.cpi.upload.endpoints.service.CpiUploadRPCOpsService
import net.corda.lifecycle.*
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.util.contextLogger

class CpiUploadRPCOpsHandler : LifecycleEventHandler {

    @VisibleForTesting
    var cpiUploadRPCOpsServiceRegistrationHandle: RegistrationHandle? = null

    companion object {
        val log = contextLogger()
    }

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {

        when (event) {
            is StartEvent -> {
                log.info("Received a start event, listening on CpiUploadRPCOpsService for its status")
                cpiUploadRPCOpsServiceRegistrationHandle = coordinator.followStatusChangesByName(setOf(
                    LifecycleCoordinatorName.forComponent<CpiUploadRPCOpsService>()
                ))
            }
            is RegistrationStatusChangeEvent -> {
                log.info("Received event ${event.status} from CpiUploadRPCOpsService, updating my status")
                if (event.status == LifecycleStatus.ERROR) {
                    closeResources()
                }
                coordinator.updateStatus(event.status)
            }
            is StopEvent -> {
                log.info("Stopping...")
                closeResources()
                coordinator.updateStatus(LifecycleStatus.DOWN)
            }
        }
    }

    private fun closeResources() {
        cpiUploadRPCOpsServiceRegistrationHandle?.close()
        cpiUploadRPCOpsServiceRegistrationHandle = null
    }
}