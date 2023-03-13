package net.corda.cpi.upload.endpoints.common

import net.corda.cpi.upload.endpoints.service.CpiUploadService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent

/**
 * Monitors the status of [CpiUploadService] so that it can know when
 * [net.corda.cpi.upload.endpoints.v1.CpiUploadRestResourceImpl.cpiUploadManager] is ready for use.
 */
internal class CpiUploadRestResourceHandler : LifecycleEventHandler {

    private companion object {
        const val FOLLOW_STATUS_NAME = "CpiUploadRestResourceHandler_FOLLOW_STATUS_NAME"
    }

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        when (event) {
            is StartEvent -> onStartEvent(coordinator)
            is RegistrationStatusChangeEvent -> onRegistrationStatusChangeEvent(event, coordinator)
            is StopEvent -> onStopEvent(coordinator)
        }
    }

    private fun onStartEvent(coordinator: LifecycleCoordinator) {
        coordinator.createManagedResource(FOLLOW_STATUS_NAME) {
            coordinator.followStatusChangesByName(
                setOf(
                    LifecycleCoordinatorName.forComponent<CpiUploadService>(),
                    LifecycleCoordinatorName.forComponent<CpiInfoReadService>()
                )
            )
        }
    }

    private fun onRegistrationStatusChangeEvent(
        event: RegistrationStatusChangeEvent,
        coordinator: LifecycleCoordinator
    ) {
        if (event.status == LifecycleStatus.ERROR) {
            coordinator.closeManagedResources(setOf(FOLLOW_STATUS_NAME))
        }
        coordinator.updateStatus(event.status)
    }

    private fun onStopEvent(coordinator: LifecycleCoordinator) {
        coordinator.closeManagedResources(setOf(FOLLOW_STATUS_NAME))
        coordinator.updateStatus(LifecycleStatus.DOWN)
    }
}
