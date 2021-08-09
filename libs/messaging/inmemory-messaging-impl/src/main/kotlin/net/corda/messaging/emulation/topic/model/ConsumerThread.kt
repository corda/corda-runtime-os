package net.corda.messaging.emulation.topic.model

import net.corda.lifecycle.LifeCycle

internal class ConsumerThread(
    private val consumer: Consumer,
    private val topic: Topic,
    private val threadFactory: (Runnable) -> Thread = { Thread(it) }
) : LifeCycle, Runnable {
    private val thread by lazy {
        threadFactory(this).also {
            it.name = "consumer thread ${consumer.groupName}-${consumer.topicName}:${consumer.hashCode()}"
            it.isDaemon = true
            it.contextClassLoader = null
        }
    }

    override fun start() {
        thread.start()
    }

    override fun stop() {
        topic.unsubscribe(consumer)
        thread.join(topic.topicConfiguration.threadStopTimeout)
    }

    override val isRunning
        get() = thread.isAlive

    override fun run() {
        topic.subscribe(consumer)
    }
}
