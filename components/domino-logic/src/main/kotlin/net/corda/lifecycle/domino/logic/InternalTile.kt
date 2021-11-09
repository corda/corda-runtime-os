package net.corda.lifecycle.domino.logic

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.v5.base.util.contextLogger

abstract class InternalTile(coordinatorFactory: LifecycleCoordinatorFactory) : DominoTile(coordinatorFactory) {
    companion object {
        private val logger = contextLogger()
    }

    abstract val children: Collection<DominoTile>

    @Volatile
    private var registrations: Collection<RegistrationHandle>? = null

    override fun startTile() {
        if (registrations == null) {
            registrations = children.map {
                it.name
            }.map {
                coordinator.followStatusChangesByName(setOf(it))
            }
            logger.info("Created $name with ${children.map { it.name }}")
        }

        startKidsIfNeeded()
    }

    override fun handleEvent(event: LifecycleEvent): Boolean {
        return when (event) {
            is RegistrationStatusChangeEvent -> {
                if (event.status == LifecycleStatus.UP) {
                    startKidsIfNeeded()
                } else {
                    val errorKids = children.filter { it.state == State.StoppedDueToError }
                    if (errorKids.isEmpty()) {
                        stop()
                    } else {
                        gotError(Exception("Had error in ${errorKids.map { it.name }}"))
                    }
                }
                true
            }
            else -> {
                false
            }
        }
    }

    private fun startKidsIfNeeded() {
        if (children.map { it.state }.contains(State.StoppedDueToError)) {
            children.filter {
                it.state != State.StoppedDueToError
            }.forEach {
                it.stop()
            }
        } else {
            children.forEach {
                it.start()
            }

            if (children.all { it.isRunning }) {
                started()
            }
        }
    }

    override fun stopTile(dueToError: Boolean) {
        children.forEach {
            if (it.state != State.StoppedDueToError) {
                it.stop()
            }
        }
    }

    override fun close() {
        registrations?.forEach {
            it.close()
        }
        super.close()
        children.reversed().forEach {
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
