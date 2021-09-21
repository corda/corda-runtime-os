package net.corda.p2p.gateway.domino

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.v5.base.util.contextLogger
import java.util.concurrent.ConcurrentLinkedDeque

abstract class LifecycleWithCoordinatorAndResources(
    coordinatorFactory: LifecycleCoordinatorFactory,
    instanceId: String?,
) : LifecycleWithCoordinator(coordinatorFactory, instanceId) {

    constructor(parent: LifecycleWithCoordinator) : this(parent.coordinatorFactory, parent.name.instanceId)

    private val stopActions = ConcurrentLinkedDeque<()->Unit>()
    private val closeActions = ConcurrentLinkedDeque<()->Unit>()
    companion object {
        private val logger = contextLogger()
    }

    fun executeBeforeClose(action: () -> Unit) {
        closeActions.addFirst(action)
    }

    fun executeBeforeStop(action: () -> Unit) {
        stopActions.addFirst(action)
    }

    override fun onStop() {
        stopActions.onEach {
            @Suppress("TooGenericExceptionCaught")
            try {
                it.invoke()
            } catch (e: Throwable) {
                logger.warn("Fail to stop", e)
            }
        }
        stopActions.clear()
        status = LifecycleStatus.DOWN
        logger.info("Stopped $name")
    }

    override fun close() {

        stop()
        // YIFT: add future
        while (status == LifecycleStatus.UP) {
            Thread.sleep(100)
        }
        closeActions.onEach {
            @Suppress("TooGenericExceptionCaught")
            try {
                it.invoke()
            } catch (e: Throwable) {
                logger.warn("Fail to close", e)
            }
        }
        closeActions.clear()
        super.close()
    }
}
