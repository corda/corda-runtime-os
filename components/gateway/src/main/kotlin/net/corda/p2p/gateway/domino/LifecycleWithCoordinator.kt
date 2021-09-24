package net.corda.p2p.gateway.domino

import net.corda.lifecycle.ErrorEvent
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.v5.base.util.contextLogger
import java.util.concurrent.atomic.AtomicReference

abstract class LifecycleWithCoordinator(
    internal val coordinatorFactory: LifecycleCoordinatorFactory,
    instanceId: String?,
) :
    Lifecycle {
    companion object {
        private val logger = contextLogger()
    }

    enum class State {
        Initialized,
        Starting,
        Resuming,
        Up,
        Pausing,
        Paused,
        Closing,
        Closed
    }
    private val currentState = AtomicReference(State.Initialized)
    enum class TransitionEvents : LifecycleEvent {
        Pause,
        Resume
    }

    val name: LifecycleCoordinatorName = LifecycleCoordinatorName(
        javaClass.name,
        instanceId
    )

    private val coordinator = coordinatorFactory.createCoordinator(name, EventHandler())

    override val isRunning: Boolean
        get() = coordinator.status == LifecycleStatus.UP

    var state: State
        get() = currentState.get()
        set(newState) {
            val oldState = currentState.getAndSet(newState)
            if ((oldState == State.Up) && (oldState != State.Up)) {
                coordinator.updateStatus(LifecycleStatus.DOWN)
            } else if ((oldState != State.Up) && (newState == State.Up)) {
                coordinator.updateStatus(LifecycleStatus.UP)
            }
            logger.info("State of $name is $newState")
        }

    open fun openSequence() {}

    override fun start() {
        logger.info("Starting $name")
        when (state) {
            State.Initialized -> {
                openSequence()
                state = State.Starting
                coordinator.start()
            }
            State.Pausing, State.Paused -> {
                state = State.Resuming
                coordinator.postEvent(TransitionEvents.Resume)
            }
            State.Starting, State.Resuming, State.Up -> {}
            State.Closing, State.Closed -> throw IllegalStateException("Can not revive the dead")
        }
    }
    abstract fun resumeSequence()

    override fun stop() {
        logger.info("Stopping $name")
        when (state) {
            State.Initialized -> {
                state = State.Pausing
                coordinator.start()
            }
            State.Starting, State.Resuming, State.Up -> {
                state = State.Pausing
                coordinator.postEvent(TransitionEvents.Pause)
            }
            State.Pausing, State.Paused, State.Closing, State.Closed -> {
            }
        }
    }
    abstract fun pauseSequence()
    open fun closeSequence() {}

    fun gotError(error: Throwable) {
        logger.info("Got error in $name", error)
        stop()
    }

    fun followStatusChanges(vararg lifecycles: LifecycleWithCoordinator): RegistrationHandle {
        return coordinator.followStatusChangesByName(lifecycles.map { it.name }.toSet())
    }

    open fun onStatusUp() {
    }
    open fun onStatusDown() {
    }

    private inner class EventHandler : LifecycleEventHandler {
        override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
            when (event) {
                is ErrorEvent -> {
                    gotError(event.cause)
                }
                is StartEvent -> {
                    when (state) {
                        State.Starting -> {
                            state = State.Resuming
                            coordinator.postEvent(TransitionEvents.Resume)
                        }
                        State.Pausing -> coordinator.postEvent(TransitionEvents.Pause)
                        else -> logger.warn("Unexpected start event, my stae is $state")
                    }
                }
                is StopEvent -> {
                    // Do nothing
                }
                is RegistrationStatusChangeEvent -> {
                    if (event.status == LifecycleStatus.UP) {
                        onStatusUp()
                    } else {
                        onStatusDown()
                    }
                }
                TransitionEvents.Resume -> {
                    resumeSequence()
                }
                TransitionEvents.Pause -> {
                    pauseSequence()
                    logger.info("Stopped $name")
                    state = State.Paused
                }
                else -> {
                    logger.warn("Unexpected event $event")
                }
            }
        }
    }

    override fun close() {
        state = State.Closing
        pauseSequence()
        closeSequence()
        coordinator.close()
        state = State.Closed
    }
}
