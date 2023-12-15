package net.corda.libs.statemanager.impl.lifecycle

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleException
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.utilities.minutes
import org.slf4j.LoggerFactory

class CheckConnectionEventHandler(
    private val componentName: LifecycleCoordinatorName,
    private val connectionCheck: () -> Unit
) : LifecycleEventHandler {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        val interval = 1.minutes
        const val CHECK_EVENT_KEY = "CheckConnection"
    }

    override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
        logger.debug("{} processing event {}", componentName, event)

        when (event) {
            is StartEvent -> {
                logger.info("$componentName is starting")
                scheduleConnectionCheck(coordinator, 0)
            }

            is StopEvent -> {
                logger.info("$componentName is stopping")
                coordinator.cancelTimer(CHECK_EVENT_KEY)
            }

            is CheckConnectionEvent -> {
                logger.debug("{} is checking connection to the underlying persistent storage", componentName)

                try {
                    connectionCheck()
                    coordinator.updateStatus(LifecycleStatus.UP, "Connection check passed")
                } catch (exception: Exception) {
                    coordinator.updateStatus(LifecycleStatus.DOWN, "Connection check failed: $exception")
                }

                scheduleConnectionCheck(coordinator)
            }
        }
    }

    fun scheduleConnectionCheck(coordinator: LifecycleCoordinator, delay: Long = interval.toMillis()) {
        // TODO-[CORE-18202]: Remove try catch once ticket is fixed.
        try {
            coordinator.setTimer(CHECK_EVENT_KEY, delay) { key -> CheckConnectionEvent(key) }
        } catch (lifecycleException: LifecycleException) {
            // Coordinator has been closed, ignore the exception until CORE-18202 is fixed
            logger.warn("Component {} is already closed, ignoring scheduling of {} event", componentName, CHECK_EVENT_KEY)
        }
    }
}
