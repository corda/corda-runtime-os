package net.corda.p2p.gateway.domino

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.RegistrationHandle
import net.corda.v5.base.util.contextLogger
import java.util.concurrent.atomic.AtomicReference

abstract class BranchTile(coordinatorFactory: LifecycleCoordinatorFactory) : DominoTile(coordinatorFactory) {
    companion object {
        private val logger = contextLogger()
    }

    abstract val children: Collection<DominoTile>

    private val registrations = AtomicReference<Collection<RegistrationHandle>>()

    override fun startTile() {
        if (registrations.get() == null) {
            val newRegistrations = children.map {
                it.name
            }.map {
                coordinator.followStatusChangesByName(setOf(it))
            }
            if (!registrations.compareAndSet(null, newRegistrations)) {
                newRegistrations.forEach {
                    it.close()
                }
            } else {
                logger.info("Created $name with ${children.map { it.name }}")
            }
        }

        children.forEach {
            it.start()
        }

        onChildStarted()
    }

    override fun onChildStarted() {
        children.filter {
            it.state != State.StoppedDueToError
        }.forEach {
            it.start()
        }
        if (children.all { it.isRunning }) {
            updateState(State.Started)
        }
    }

    override fun onChildStopped() {
        stop()
    }

    override fun stopTile() {
        children.forEach {
            if (it.state != State.StoppedDueToError) {
                it.stop()
            }
        }
    }

    override fun close() {
        registrations.getAndSet(null)?.forEach {
            it.close()
        }
        super.close()
        children.reversed().forEach {
            @Suppress("TooGenericExceptionCaught")
            try {
                it.close()
            } catch (e: Throwable) {
                logger.warn("Could not close $it", e)
            }
        }
    }
    override fun toString(): String {
        return "$name: $state: $children"
    }
}
