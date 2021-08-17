package net.corda.messaging.emulation.topic.model

import net.corda.lifecycle.Lifecycle
import net.corda.messaging.emulation.properties.SubscriptionConfiguration

class ConsumerThread(
    private val consumer: Consumer,
    private val topic: Topic,
    private val subscriptionConfig: SubscriptionConfiguration,
    private val threadFactory: (Runnable) -> Thread = { Thread(it) }
) : Lifecycle, Runnable {
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
        thread.join(subscriptionConfig.threadStopTimeout.toMillis())
    }

    override val isRunning
        get() = thread.isAlive

    override fun run() {
        topic.subscribe(consumer, subscriptionConfig)
    }
}
