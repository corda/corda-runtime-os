package net.corda.lifecycle.domino.logic

import net.corda.lifecycle.ErrorEvent
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleException
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.v5.base.util.contextLogger
import java.util.concurrent.ConcurrentHashMap

abstract class DominoTile(
    coordinatorFactory: LifecycleCoordinatorFactory,
) : Lifecycle {
    companion object {
        private val logger = contextLogger()
        private val instancesIndex = ConcurrentHashMap<String, Int>()
    }
    private object StartTile : LifecycleEvent
    private data class StopTile(val dueToError: Boolean) : LifecycleEvent
    private data class UpdateState(val state: State) : LifecycleEvent
    private class CallFromCoordinator(val callback: () -> Unit) : LifecycleEvent
    enum class State {
        Created,
        Started,
        StoppedDueToError,
        StoppedByParent
    }
    val name = LifecycleCoordinatorName(
        javaClass.simpleName,
        instancesIndex.compute(javaClass.simpleName) { _, last ->
            if (last == null) {
                1
            } else {
                last + 1
            }
        }.toString()
    )

    override fun start() {
        logger.info("Starting $name")
        when (state) {
            State.Created -> {
                coordinator.start()
                coordinator.postEvent(StartTile)
            }
            State.Started -> {
                // Do nothing
            }
            State.StoppedByParent -> {
                coordinator.postEvent(StartTile)
            }
            State.StoppedDueToError -> {
                logger.warn("Can not start $name, it was stopped due to an error")
            }
        }
    }

    override fun stop() {
        if (state != State.StoppedByParent) {
            coordinator.postEvent(StopTile(false))
            coordinator.postEvent(UpdateState(State.StoppedByParent))
        }
    }

    protected val coordinator = coordinatorFactory.createCoordinator(name, EventHandler())

    @Volatile
    private var currentState = State.Created

    val state: State
        get() = currentState

    override val isRunning: Boolean
        get() = state == State.Started

    protected open fun started() {
        coordinator.postEvent(UpdateState(State.Started))
    }
    private fun updateState(newState: State) {
        if (newState != currentState) {
            val status = when (newState) {
                State.Started -> LifecycleStatus.UP
                State.Created, State.StoppedByParent -> LifecycleStatus.DOWN
                State.StoppedDueToError -> LifecycleStatus.ERROR
            }
            currentState = newState
            coordinator.updateStatus(status)
            logger.info("State of $name is $newState")
        }
    }

    open fun handleEvent(event: LifecycleEvent): Boolean = false

    fun callFromCoordinator(callback: () -> Unit) {
        coordinator.postEvent(CallFromCoordinator(callback))
    }

    private inner class EventHandler : LifecycleEventHandler {
        override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
            when (event) {
                is ErrorEvent -> {
                    gotError(event.cause)
                }
                is StartEvent -> {
                    // Do Nothing
                }
                is StopEvent -> {
                    // Do nothing
                }
                is StopTile -> {
                    stopTile(event.dueToError)
                }
                is StartTile -> {
                    startTile()
                }
                is UpdateState -> {
                    updateState(event.state)
                }
                is CallFromCoordinator -> {
                    event.callback.invoke()
                }
                else -> {
                    if (!handleEvent(event)) {
                        logger.warn("Unexpected event $event")
                    }
                }
            }
        }
    }

    protected open fun gotError(cause: Throwable) {
        logger.warn("Got error in $name", cause)
        if (state != State.StoppedDueToError) {
            coordinator.postEvent(StopTile(true))
            coordinator.postEvent(UpdateState(State.StoppedDueToError))
        }
    }

    protected abstract fun startTile()
    protected abstract fun stopTile(dueToError: Boolean)

    override fun close() {
        stopTile(false)

        try {
            coordinator.close()
        } catch (e: LifecycleException) {
            // This try-catch should be removed once CORE-2786 is fixed
            logger.debug("Could not close coordinator", e)
        }
    }

    override fun toString(): String {
        return "$name: $state"
    }
}
