package net.corda.p2p.gateway.domino

import net.corda.lifecycle.ErrorEvent
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.v5.base.util.contextLogger
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicReference

abstract class LifecycleWithCoordinator(
    private val coordinatorFactory: LifecycleCoordinatorFactory,
    instanceId: String
) :
    Lifecycle {

    constructor(parent: LifecycleWithCoordinator) : this(
        parent.coordinatorFactory,
        parent.name.toString()
    )

    companion object {
        private val logger = contextLogger()
    }

    enum class State {
        Created,
        Started,
        StoppedDueToError,
        StoppedByParent
    }
    private val currentState = AtomicReference(State.Created)

    val name: LifecycleCoordinatorName = LifecycleCoordinatorName(
        javaClass.simpleName,
        instanceId
    )

    private val coordinator = coordinatorFactory.createCoordinator(name, EventHandler())

    override val isRunning: Boolean
        get() = state == State.Started

    var state: State
        get() =
            currentState.get()
        set(newState) {
            val oldState = currentState.getAndSet(newState)
            if ((newState != State.Started) && (oldState == State.Started)) {
                coordinator.updateStatus(LifecycleStatus.DOWN)
            } else if ((oldState != State.Started) && (newState == State.Started)) {
                coordinator.updateStatus(LifecycleStatus.UP)
            }
            logger.info("State of $name is $newState")
        }

    abstract val children: Collection<LifecycleWithCoordinator>

    private val closeActions = ConcurrentLinkedDeque<()->Unit>()
    fun executeBeforeClose(action: () -> Unit) {
        closeActions.addFirst(action)
    }
    private val stopActions = ConcurrentLinkedDeque<()->Unit>()
    fun executeBeforeStop(action: () -> Unit) {
        stopActions.addFirst(action)
    }

    open fun startSequence() {}

    override fun start() {
        logger.info("Starting $name")
        when (state) {
            State.Created -> {
                coordinator.start()
                children.map {
                    it.name
                }
                    .map {
                        coordinator.followStatusChangesByName(setOf(it))
                    }.forEach {
                        executeBeforeClose(it::close)
                    }
            }
            State.Started -> {
                // Do nothing
            }
            State.StoppedByParent -> {
                children.forEach {
                    it.start()
                }
                onStart()
            }
            State.StoppedDueToError -> {
                logger.info("Can not start $name, it was stopped due to an error")
            }
        }
    }

    private fun onStart() {
        if (children.all { it.isRunning }) {
            startSequence()
        }
    }

    private fun stopSequence() {
        stopActions.onEach {
            @Suppress("TooGenericExceptionCaught")
            try {
                it.invoke()
            } catch (e: Throwable) {
                logger.warn("Fail to stop", e)
            }
        }
        stopActions.clear()
    }

    override fun stop() {
        logger.info("Stopping $name")
        when (state) {
            State.Created -> {
                state = State.StoppedByParent
            }
            State.Started -> {
                children.forEach {
                    it.stop()
                }
                stopSequence()
                state = State.StoppedByParent
            }
            State.StoppedByParent -> {
                // Nothing to do
            }
            State.StoppedDueToError -> {
                state = State.StoppedByParent
            }
        }
    }

    fun gotError(error: Throwable) {
        logger.info("Got error in $name", error)
        when (state) {
            State.Created -> {
                state = State.StoppedDueToError
            }
            State.Started -> {
                stopSequence()
                state = State.StoppedDueToError
            }
            State.StoppedByParent -> {
                // Nothing to do
            }
            State.StoppedDueToError -> {
                // Nothing to do
            }
        }
    }

    private inner class EventHandler : LifecycleEventHandler {
        override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
            when (event) {
                is ErrorEvent -> {
                    gotError(event.cause)
                }
                is StartEvent -> {
                    when (state) {
                        State.Created -> {
                            children.forEach {
                                it.start()
                            }
                            onStart()
                        }
                        else -> logger.warn("Unexpected start event, my state is $state")
                    }
                }
                is StopEvent -> {
                    // Do nothing
                }
                is RegistrationStatusChangeEvent -> {
                    if (event.status == LifecycleStatus.UP) {
                        onStart()
                    } else {
                        // A child went down, stop my self
                        stop()
                    }
                }
                else -> {
                    logger.warn("Unexpected event $event")
                }
            }
        }
    }

    override fun close() {
        stopSequence()

        closeActions.onEach {
            @Suppress("TooGenericExceptionCaught")
            try {
                it.invoke()
            } catch (e: Throwable) {
                logger.warn("Fail to close", e)
            }
        }
        closeActions.clear()

        children.reversed().forEach {
            it.close()
        }
        coordinator.close()
    }
}
