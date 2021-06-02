package net.corda.lifecycle

import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class SimpleLifeCycleCoordinator(
    override val batchSize: Int,
    override val lifeCycleProcessor: LifeCycleProcessor,
    override val timeout: Long,
) : LifeCycleCoordinator {

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
            lifeCycleProcessor.processEvent(lifeCycleEvent)
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
                executorService = Executors.newSingleThreadScheduledExecutor {
                    val thread = Thread()
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
     * Having a reference of the [executorService] before to set it to `null`...
     *
     */
    override fun stop(): Boolean {
        lock.withLock {
            val executorService = lock.withLock {
                val executorService = this.executorService
                this.executorService = null
                executorService
            }
        }
        executorService?.apply {
            eventQueue.offer(StopEvent)
            submit(::processEvents)
            shutdown()
            return awaitTermination(timeout, TimeUnit.MILLISECONDS)
        }
        // executorService is `null` hence terminated before => return `true`.
        return true
    }
}