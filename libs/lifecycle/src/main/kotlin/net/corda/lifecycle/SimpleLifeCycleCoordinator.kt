package net.corda.lifecycle

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class SimpleLifeCycleCoordinator(
    override val batchSize: Int,
    override val timeout: Long,
    override val lifeCycleProcessor: (lifeCycleEvent: LifeCycleEvent) -> Unit,
) : LifeCycleCoordinator {

    companion object {

        private val logger: Logger = LoggerFactory.getLogger(LifeCycleCoordinator::class.java)

    } //~ companion

    private val lock = ReentrantLock()

    /**
     * Must be synchronized with [lock].
     */
    @Volatile
    private var executorService: ScheduledExecutorService? = null

    private val eventQueue = ConcurrentLinkedDeque<LifeCycleEvent>()

    private val isScheduled = AtomicBoolean(false)

    private val timerMap = ConcurrentHashMap<String, ScheduledFuture<*>>()

    private fun processEvents() {
        val eventList = ArrayList<LifeCycleEvent>(batchSize)
        for (i in 0 until batchSize) {
            val lifeCycleEvent = eventQueue.poll() ?: break
            eventList.add(lifeCycleEvent)
        }
        for (lifeCycleEvent in eventList) {
            lifeCycleProcessor.invoke(lifeCycleEvent)
        }
        isScheduled.set(false)
        if (eventQueue.isNotEmpty()) {
            scheduleIfRequired()
        }
    }

    private fun scheduleIfRequired() {
        val executorService = this.executorService ?: return
        if (!isScheduled.getAndSet(true)) {
            executorService.submit(::processEvents)
        }
    }

    //: LifeCycleCoordinator

    override fun cancelTimer(key: String) {
        timerMap[key]?.cancel(false)
        val eventQueueIterator = eventQueue.iterator()
        while (eventQueueIterator.hasNext()) {
            val lifeCycleEvent = eventQueueIterator.next()
            if (lifeCycleEvent is TimerEvent && lifeCycleEvent.key == key) {
                eventQueueIterator.remove()
            }
        }
    }

    override fun postEvent(lifeCycleEvent: LifeCycleEvent) {
        eventQueue.offer(lifeCycleEvent)
        scheduleIfRequired()
    }


    override fun setTimer(key: String, delay: Long, onTime: (String) -> TimerEvent) {
        val executorService = this.executorService ?: return
        cancelTimer(key)
        timerMap[key] = executorService.schedule({ postEvent(onTime(key)) }, delay, TimeUnit.MILLISECONDS)
    }

    //: LifeCycle

    override val isRunning: Boolean
        get() = lock.withLock { (executorService != null) }

    override fun start() {
        lock.withLock {
            if (executorService == null) {
                executorService = Executors.newSingleThreadScheduledExecutor { runnable ->
                    val thread = Thread(runnable)
                    thread.isDaemon = true
                    thread
                }
                isScheduled.set(false)
                postEvent(StartEvent)
            }
        }
    }

    /**
     * Stop this [LifeCycleCoordinator] processing remaining [LifeCycleEvent] in [eventQueue].
     *
     * This method stops the coordinator setting the [executorService] to `null` first
     * to flag this [LifeCycleCoordinator] stopped.
     * See [isRunning].
     *
     */
    override fun stop() {
        val executorService = lock.withLock {
            lock.withLock {
                val executorService = this.executorService
                this.executorService = null
                executorService
            }
        }
        executorService?.apply {
            eventQueue.offer(StopEvent)
            submit(::processEvents)
            shutdown()
            if (!awaitTermination(timeout, TimeUnit.MILLISECONDS)) {
                logger.warn("Stop: timeout after $timeout ms.")
            }
        }
    }
}