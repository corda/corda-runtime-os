package net.corda.messaging.emulation.topic.model

import java.time.Duration

class ConsumptionThread(
    private val threadName: String,
    private val timeout: Duration,
    private val killMe: () -> Unit,
    private val loop: Runnable,
    private val threadFactory: (Runnable) -> Thread = { Thread(it) }
) : Consumption {
    private val thread by lazy {
        threadFactory(loop).also {
            it.name = threadName
            it.isDaemon = true
        }
    }

    override fun start() {
        thread.start()
    }

    override fun stop() {
        killMe()
        thread.join(timeout.toMillis())
    }

    override val isRunning
        get() = thread.isAlive
}
