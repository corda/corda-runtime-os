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
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.util.contextLogger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

abstract class DominoTile(
    coordinatorFactory: LifecycleCoordinatorFactory,
) : Lifecycle {
    companion object {
        private val logger = contextLogger()
        private val instancesIndex = ConcurrentHashMap<String, Int>()
    }
    private object StartTile : LifecycleEvent
    private data class StopTile(val dueToError: Boolean) : LifecycleEvent
    private class CallFromCoordinator(val callback: () -> Unit) : LifecycleEvent
    enum class State {
        Created,
        Started,
        StoppedDueToError,
        StoppedByParent
    }
    // This needs to be open so that `startTile will register to follow all the tiles on the first run` and `startTile will not register to
    // follow any tile for the second time` pass (as they set this variable). This should be fixed (maybe mockito-inline fixes this).
    open val name = LifecycleCoordinatorName(
        javaClass.simpleName,
        instancesIndex.compute(javaClass.simpleName) { _, last ->
            if (last == null) {
                1
            } else {
                last + 1
            }
        }.toString()
    )

    private val controlLock = ReentrantReadWriteLock()

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
        }
    }

    fun <T> withLifecycleLock(access: () -> T): T {
        return controlLock.read {
            access.invoke()
        }
    }

    protected val coordinator = coordinatorFactory.createCoordinator(name, EventHandler())
    protected val resources = ResourcesHolder()
    open val children: Collection<DominoTile> = emptySet()
    @Volatile
    private var registrations: Collection<RegistrationHandle>? = null

    private val currentState = AtomicReference(State.Created)

    private val isOpen = AtomicBoolean(true)

    val state: State
        get() = currentState.get()

    override val isRunning: Boolean
        get() = state == State.Started

    protected open fun started() {
        @Suppress("TooGenericExceptionCaught")
        try {
            resources.close()
            createResources()
            updateState(State.Started)
        } catch (e: Throwable) {
            gotError(e)
        }
    }
    private fun updateState(newState: State) {
        val oldState = currentState.getAndSet(newState)
        if (newState != oldState) {
            val status = when (newState) {
                State.Started -> LifecycleStatus.UP
                State.Created, State.StoppedByParent -> LifecycleStatus.DOWN
                State.StoppedDueToError -> LifecycleStatus.ERROR
            }
            controlLock.write {
                coordinator.updateStatus(status)
            }
            logger.info("State of $name is $newState")
        }
    }

    @VisibleForTesting
    internal open fun handleEvent(event: LifecycleEvent): Boolean {
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

    fun callFromCoordinator(callback: () -> Unit) {
        coordinator.postEvent(CallFromCoordinator(callback))
    }

    private inner class EventHandler : LifecycleEventHandler {
        private fun handleControlEvent(event: LifecycleEvent) {
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
                    if (event.dueToError) {
                        updateState(State.StoppedDueToError)
                    } else {
                        updateState(State.StoppedByParent)
                    }
                }
                is StartTile -> {
                    startTile()
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

        override fun processEvent(event: LifecycleEvent, coordinator: LifecycleCoordinator) {
            if (!isOpen.get()) {
                return
            }

            controlLock.write {
                handleControlEvent(event)
            }
        }
    }

    protected open fun gotError(cause: Throwable) {
        logger.warn("Got error in $name", cause)
        if (state != State.StoppedDueToError) {
            coordinator.postEvent(StopTile(true))
        }
    }

    internal open fun startTile() {
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

    internal open fun stopTile(dueToError: Boolean) {
        resources.close()
        children.forEach {
            if (it.state != State.StoppedDueToError) {
                it.stop()
            }
        }
    }

    /**
     * Override this if your tile needs to create and destroy resources (e.g. a thread pool) when starting and stopping, respectively.
     */
    open fun createResources() {}

    override fun close() {
        registrations?.forEach {
            it.close()
        }
        controlLock.write {
            isOpen.set(false)

            stopTile(false)

            try {
                coordinator.close()
            } catch (e: LifecycleException) {
                // This try-catch should be removed once CORE-2786 is fixed
                logger.debug("Could not close coordinator", e)
            }
        }
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
