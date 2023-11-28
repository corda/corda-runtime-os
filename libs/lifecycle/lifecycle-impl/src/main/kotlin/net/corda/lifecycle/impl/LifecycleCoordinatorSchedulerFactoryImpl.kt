package net.corda.lifecycle.impl

import com.google.common.util.concurrent.ThreadFactoryBuilder
import net.corda.lifecycle.LifecycleCoordinatorScheduler
import net.corda.lifecycle.LifecycleCoordinatorSchedulerFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors

@Component(service = [ LifecycleCoordinatorSchedulerFactory::class ])
class LifecycleCoordinatorSchedulerFactoryImpl @Activate constructor() : LifecycleCoordinatorSchedulerFactory {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private fun handleUncaughtException(thread: Thread, exception: Throwable) {
        logger.error("Uncaught exception from ${thread.name}", exception)
    }

    /**
     * The executor on which events are processed. Note that all events should be processed on an executor thread,
     * but they may be posted from any thread. Different events may be processed on different executor threads.
     *
     * The coordinator guarantees that the event processing task is only scheduled once. This means that event
     * processing is effectively single threaded in the sense that no event processing will happen concurrently.
     *
     * By sharing a thread pool among coordinators, it should be possible to reduce resource usage when in a stable
     * state.
     */
    private val executor = Executors.newCachedThreadPool(
        ThreadFactoryBuilder()
            .setUncaughtExceptionHandler(::handleUncaughtException)
            .setNameFormat("lifecycle-coordinator-%d")
            .setDaemon(true)
            .build()
    )

    private val timerExecutor = Executors.newSingleThreadScheduledExecutor(
        ThreadFactoryBuilder()
            .setUncaughtExceptionHandler(::handleUncaughtException)
            .setNameFormat("lifecycle-coordinator-timer-%d")
            .setDaemon(true)
            .build()
    )

    override fun create(): LifecycleCoordinatorScheduler {
        return LifecycleCoordinatorSchedulerImpl(executor, timerExecutor)
    }

    @Suppress("unused")
    @Deactivate
    fun done() {
        timerExecutor.shutdown()
        executor.shutdown()
    }
}
