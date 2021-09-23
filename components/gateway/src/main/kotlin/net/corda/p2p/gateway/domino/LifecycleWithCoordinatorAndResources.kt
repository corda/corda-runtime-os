package net.corda.p2p.gateway.domino

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.v5.base.util.contextLogger
import java.util.concurrent.ConcurrentLinkedDeque

abstract class LifecycleWithCoordinatorAndResources(
    coordinatorFactory: LifecycleCoordinatorFactory,
    instanceId: String?,
) : LifecycleWithCoordinator(coordinatorFactory, instanceId) {

    constructor(parent: LifecycleWithCoordinator) : this(parent.coordinatorFactory, parent.name.instanceId)

    private val pauseActions = ConcurrentLinkedDeque<()->Unit>()
    private val closeActions = ConcurrentLinkedDeque<()->Unit>()
    companion object {
        private val logger = contextLogger()
    }

    fun executeBeforeClose(action: () -> Unit) {
        closeActions.addFirst(action)
    }

    fun executeBeforePause(action: () -> Unit) {
        pauseActions.addFirst(action)
    }

    override fun pauseSequence() {
        pauseActions.onEach {
            @Suppress("TooGenericExceptionCaught")
            try {
                it.invoke()
            } catch (e: Throwable) {
                logger.warn("Fail to stop", e)
            }
        }
        pauseActions.clear()
        logger.info("Stopped $name")
    }

    override fun closeSequence() {
        closeActions.onEach {
            @Suppress("TooGenericExceptionCaught")
            try {
                it.invoke()
            } catch (e: Throwable) {
                logger.warn("Fail to close", e)
            }
        }
        closeActions.clear()
    }
}
