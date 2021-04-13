package impl.samples

import impl.samples.processor.impl.StateAndEventProcessorStrings
import impl.samples.processor.impl.DurableProcessorLongs
import impl.samples.processor.impl.DurableProcessorStrings
import impl.samples.processor.impl.PubSubProcessorStrings
import impl.samples.subscription.factory.impl.SubscriptionFactoryImpl
import net.corda.messaging.api.subscription.factory.config.StateAndEventSubscriptionConfig
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import java.util.concurrent.Executors

fun main() {
    PubSubExample().start()
    DurableQueueExample().start()
    ActorModeExample().start()
}

class PubSubExample {
    fun start() {
        val executorService = Executors.newFixedThreadPool(1)
        val properties = mapOf<String, String>()
        val processor = PubSubProcessorStrings()
        val factory =  SubscriptionFactoryImpl()
        val subscription =  factory.createPubSubSubscription(
            SubscriptionConfig("groupName",
            1,
            ""),
            processor,
            executorService,
            properties)

        subscription.start()
        Thread.sleep(2)
        subscription.stop()
    }
}

class DurableQueueExample {
    fun start() {
        val properties = mapOf<String, String>()
        val processorStrings = DurableProcessorStrings()
        val processorLongs = DurableProcessorLongs()

        val factory = SubscriptionFactoryImpl()

        val subscription =  factory.createDurableSubscription(
            SubscriptionConfig("groupName1",
            1,
            ""),
            processorStrings,
            properties)

        val subscription2 =  factory.createDurableSubscription(
            SubscriptionConfig("groupName6",
            6,
            ""),
            processorLongs,
            properties)

        subscription.start()
        subscription2.start()
        Thread.sleep(10)
        subscription.stop()
        subscription2.stop()
    }
}

class ActorModeExample {
    fun start() {
        val properties = mapOf<String, String>()
        val processor = StateAndEventProcessorStrings()
        val factory =  SubscriptionFactoryImpl()
        val subscription =  factory.createStateAndEventSubscription(
            StateAndEventSubscriptionConfig("groupName",
            1,
            "event",
            "state"),
            processor,
            properties)

        subscription.start()
        Thread.sleep(10)
        subscription.stop()
    }
}
