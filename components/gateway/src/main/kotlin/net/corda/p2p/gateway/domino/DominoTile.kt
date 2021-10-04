package net.corda.p2p.gateway.domino

import net.corda.lifecycle.Lifecycle
import net.corda.v5.base.util.contextLogger
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicReference

abstract class DominoTile(
    private val parent: DominoTile?
) :
    Lifecycle {

    companion object {
        private val logger = contextLogger()
    }

    enum class State {
        Created,
        Running,
        Error,
        Stopped
    }
    private val currentState = AtomicReference(State.Created)

    val name: String = if (parent == null) {
        javaClass.simpleName
    } else {
        "${parent.name}->${javaClass.simpleName}"
    }

    override val isRunning: Boolean
        get() = state == State.Running

    var state: State
        get() =
            currentState.get()
        set(newState) {
            val oldState = currentState.getAndSet(newState)
            if ((newState != State.Running) && (oldState == State.Running)) {
                parent?.stop()
            } else if ((oldState != State.Running) && (newState == State.Running)) {
                parent?.onStart()
            }
            logger.info("State of $name is $newState")
        }

    abstract val children: Collection<DominoTile>

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
            State.Running -> {
                // Do nothing
            }
            State.Stopped, State.Created -> {
                onStart()
            }
            State.Error -> {
                logger.info("Can not start $name, it was stopped due to an error")
            }
        }
    }

    private fun onStart() {
        children.filter {
            (it.state == State.Stopped) || (it.state == State.Created)
        }.forEach {
            it.start()
        }

        if (children.all { it.isRunning }) {
            @Suppress("TooGenericExceptionCaught")
            try {
                startSequence()
            } catch (e: Throwable) {
                gotError(e)
            }
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
                state = State.Stopped
            }
            State.Running -> {
                children.forEach {
                    if (it.state != State.Error) {
                        it.stop()
                    }
                }
                stopSequence()
                state = State.Stopped
            }
            State.Stopped -> {
                // Nothing to do
            }
            State.Error -> {
                state = State.Stopped
            }
        }
    }

    fun gotError(error: Throwable) {
        logger.info("Got error in $name", error)
        when (state) {
            State.Created -> {
                state = State.Error
            }
            State.Running, State.Stopped -> {
                stopSequence()
                children.forEach {
                    it.stop()
                }
                state = State.Error
            }
            State.Error -> {
                // Nothing to do
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
    }

    override fun toString(): String {
        return "$name: $state: $children"
    }
}
