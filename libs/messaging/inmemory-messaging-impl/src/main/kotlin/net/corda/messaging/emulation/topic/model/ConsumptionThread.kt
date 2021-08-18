package net.corda.messaging.emulation.topic.model

import net.corda.messaging.emulation.properties.SubscriptionConfiguration

class ConsumptionThread(
    private val consumerDefinitions: ConsumerDefinitions,
    private val topic: Topic,
    private val subscriptionConfig: SubscriptionConfiguration,
    private val threadFactory: (Runnable) -> Thread = { Thread(it) }
) : Consumption, Runnable {
    private val thread by lazy {
        threadFactory(this).also {
            it.name = "consumer thread ${consumerDefinitions.groupName}-${consumerDefinitions.topicName}:${consumerDefinitions.hashCode()}"
            it.isDaemon = true
        }
    }

    override fun start() {
        thread.start()
    }

    override fun stop() {
        topic.unsubscribe(consumerDefinitions)
        thread.join(subscriptionConfig.threadStopTimeout.toMillis())
    }

    override val isRunning
        get() = thread.isAlive

    override fun run() {
        topic.subscribe(consumerDefinitions, subscriptionConfig)
    }
}
