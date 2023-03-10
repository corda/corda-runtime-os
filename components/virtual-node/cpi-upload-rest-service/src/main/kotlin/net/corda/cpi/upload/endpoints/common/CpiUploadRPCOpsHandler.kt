package net.corda.cpi.upload.endpoints.common

import net.corda.cpi.upload.endpoints.service.CpiUploadRPCOpsService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.utilities.VisibleForTesting

/**
 * Monitors the status of [CpiUploadRPCOpsService] so that it can know when [CpiUploadRPCOpsImpl.cpiUploadManager] is ready for use.
 */
internal class CpiUploadRPCOpsHandler : LifecycleEventHandler {

    @VisibleForTesting
    internal var cpiUploadRPCOpsServiceRegistrationHandle: RegistrationHandle? = null

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> onStartEvent(coordinator)
            is RegistrationStatusChangeEvent -> onRegistrationStatusChangeEvent(event, coordinator)
            is StopEvent -> onStopEvent(coordinator)
        }
    }

    private fun onStartEvent(coordinator: LifecycleCoordinator) {
        cpiUploadRPCOpsServiceRegistrationHandle = coordinator.followStatusChangesByName(
            setOf(
                LifecycleCoordinatorName.forComponent<CpiUploadRPCOpsService>(),
                LifecycleCoordinatorName.forComponent<CpiInfoReadService>()
            )
        )
    }

    private fun onRegistrationStatusChangeEvent(
        event: RegistrationStatusChangeEvent,
        coordinator: LifecycleCoordinator
    ) {
        if (event.status == LifecycleStatus.ERROR) {
            closeResources()
        }
        coordinator.updateStatus(event.status)
    }

    private fun onStopEvent(coordinator: LifecycleCoordinator) {
        closeResources()
        coordinator.updateStatus(LifecycleStatus.DOWN)
    }

    private fun closeResources() {
        cpiUploadRPCOpsServiceRegistrationHandle?.close()
        cpiUploadRPCOpsServiceRegistrationHandle = null
    }
}
