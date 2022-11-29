package net.corda.messaging.subscription

import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleStatus
import net.corda.messaging.config.ResolvedSubscriptionConfig
import org.slf4j.Logger
import java.lang.IllegalStateException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

/**
 * A helper class which creates a thread which runs a "looper" function in that thread. Technically the function doesn't
 * have to loop at all, however this class provides access to an api which makes it easy to write functions that do want
 * to run in a continuous loop, until either an external request to close, or the loop itself decides to close down the
 * thread.
 *
 * To keep your looper function running, all you need to do is loop whilst [loopStopped] remains false.
 *
 * The constructor takes a [LifecycleCoordinatorFactory] in order that a [lifecycleCoordinator] is created on behalf of
 * the user of this class. [LifecycleStatus.UP] events are not handled by this class, it is assumed your function has
 * some initialisation logic to do before announcing it is UP. Note however that [LifecycleStatus.DOWN] events are
 * handled by the lifecycle coordinator whenever it is closed, which the [ThreadLooper] handles for you when the loop
 * drops out. That means you do not need to post explicit [LifecycleStatus.DOWN] events at the end of the loop function.
 * To raise UP or other lifecycle events at any time, the [updateLifecycleStatus] can be called.
 */
@Suppress("LongParameterList")
class ThreadLooper(
    private val log: Logger,
    private val config: ResolvedSubscriptionConfig,
    lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    private val threadNamePrefix: String,
    private val loopFunction: () -> Unit,
    private val threadFactory: (String, () -> Unit) -> Thread = { name, block ->
        thread(
            start = true,
            isDaemon = true,
            contextClassLoader = null,
            name = name,
            priority = -1,
            block = block,
        )
    },
) : LifecycleStatusUpdater {
    @Volatile
    private var _isRunning = true

    /**
     * [loopStopped] and [isRunning] are not indicating the same. [loopStopped] is an indication to the looper function
     * passed to [ThreadLooper] that it should drop out. However it is not obliged to drop out immediately, it really
     * means don't process any more loop iterations after the current one. This means the ThreadLooper is still running
     * for all purposes for a time after [loopStopped] is true. It is only ever to be used inside the looper function.
     *
     * [isRunning] is the public facing status of the [ThreadLooper] as a black box. Is it set to false only after the
     * looper function drops out, and it's the status external entities can consider the process to be finished. If you
     * are exposing a status of whether or not the [ThreadLooper] is finished, this is the property that should be used.
     */
    val loopStopped: Boolean
        get() = stoppableThread.loopStopped

    val isRunning: Boolean
        get() = _isRunning

    val lifecycleCoordinatorName
        get() = lifecycleCoordinator.name

    private val lifecycleCoordinator =
        lifecycleCoordinatorFactory.createCoordinator(config.lifecycleCoordinatorName) { _, _ -> }

    /**
     * Thread handling and loop status isolated to this class to ensure all public facing operations are performed under
     * lock and all variables pertaining to the thread and loop are in sync.
     */
    private inner class StoppableThread {
        private val lock = ReentrantLock()
        private var thread: Thread? = null
        private var _loopStopped = false

        fun stopFromWithin() {
            lock.withLock {
                if (_loopStopped) return
                check(Thread.currentThread().id == thread?.id) { "Invalid function call from within thread" }
                doStopLoopAndClearThread()
            }
        }

        fun stopAndJoin() {
            val threadToJoin = lock.withLock {
                if (_loopStopped) return
                check(Thread.currentThread().id != thread?.id) { "Invalid function call from outside thread" }
                doStopLoopAndClearThread()
            }
            // Do not wait to join the thread under lock or we can create deadlocks as the loop function in this thread
            // is continuously asking for the value of loopStopped which also attempts to acquire the lock
            threadToJoin.join(config.threadStopTimeout.toMillis())
        }

        fun start() {
            lock.withLock {
                if (thread != null) return

                _loopStopped = false
                lifecycleCoordinator.start()
                thread = threadFactory(
                    "$threadNamePrefix ${config.group}-${config.topic}",
                    ::runConsumeLoop,
                )
            }
        }

        val loopStopped: Boolean
            get() = lock.withLock {
                _loopStopped
            }

        /**
         * Must be called from within the lock
         */
        private fun doStopLoopAndClearThread(): Thread = thread.let {
            _loopStopped = true
            thread = null
            it// return original Thread
        } ?: throw IllegalStateException("Clearing state, thread was null on non-stopped ThreadLooper")
    }

    private val stoppableThread = StoppableThread()

    fun start() {
        _isRunning = true
        stoppableThread.start()
    }

    /**
     * Close the looper from outside the loop. This will set [loopStopped] to false so the looper function knows to drop
     * out. It will then join the looper thread, so will not return until the looper function and thread has completed
     * executing. It is the responsibility of the looper function to ensure it can drop out when [loopStopped] is false.
     * The function will not hang indefinitely if this contract is not adhered to, it will timeout, but this should be
     * considered a programming error.
     *
     * @throws IllegalStateException if called from within the looper function.
     */
    fun close() {
        stoppableThread.stopAndJoin()
    }

    /**
     * Close the looper from within the loop. This will set [loopStopped] to false so the looper function knows to drop out.
     * It is up to the implementation of the looper function to adhere to this contract, this method will not interrupt
     * the thread or do anything else special to bring the function to an early close.
     *
     * @throws IllegalStateException if called from outside the looper function.
     */
    fun stopLoop() {
        stoppableThread.stopFromWithin()
    }

    override fun updateLifecycleStatus(newStatus: LifecycleStatus) {
        if (isRunning) {
            lifecycleCoordinator.updateStatus(newStatus)
        }
    }

    override fun updateLifecycleStatus(newStatus: LifecycleStatus, reason: String) {
        if (isRunning) {
            lifecycleCoordinator.updateStatus(newStatus, reason)
        }
    }

    private fun runConsumeLoop() {
        // As the thread entry point, ensure nothing leaks to the uncaught exception handler. This just means we at least
        // channel uncaught Throwables through the correct logger. The subscription would still be in a bad state caused
        // by an apparent programming error at this point.
        try {
            loopFunction()
            _isRunning = false
            lifecycleCoordinator.close()
        } catch (t: Throwable) {
            val msg = "runConsumeLoop Throwable caught, subscription in an unrecoverable bad state"
            log.error(msg, t)
            lifecycleCoordinator.updateStatus(LifecycleStatus.ERROR, msg)
        } finally {
            _isRunning = false
        }
    }
}
