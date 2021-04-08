package api.samples

import api.samples.producerconsumer.processor.impl.SimpleProcessor
import api.samples.producerconsumer.processor.impl.SimpleActorProcessor
import api.samples.producerconsumer.subscription.ActorSubscription
import api.samples.producerconsumer.subscription.DurableQueueSubscription
import api.samples.producerconsumer.subscription.PubSubSubscription
import java.util.concurrent.Executors

fun main() {
    DurableQueueExample().start()
    PubSubExample().start()
    ActorModeExample().start()
}

class PubSubExample {
    fun start() {
        val executorService = Executors.newFixedThreadPool(1)
        val properties = mapOf<String, String>()
        val processor = SimpleProcessor()
        val subscription =  PubSubSubscription("TOPIC_1", processor, executorService, properties)

        subscription.start()
        Thread.sleep(10)
        subscription.pause()

        Thread.sleep(1000)
        subscription.play()
        Thread.sleep(10)
        subscription.cancel()
    }
}

class DurableQueueExample {
    fun start() {
        val properties = mapOf<String, String>()
        val processor = SimpleProcessor()
        val subscription =  DurableQueueSubscription("TOPIC_1", processor, properties)

        subscription.start()
        Thread.sleep(10)
        subscription.pause()

        Thread.sleep(1000)
        subscription.play()
        Thread.sleep(10)
        subscription.cancel()
    }
}

class ActorModeExample {
    fun start() {
        val properties = mapOf<String, String>()
        val processor = SimpleActorProcessor()
        val subscription =  ActorSubscription("TOPIC_1_EVENT","TOPIC_1_STATE", processor, properties)

        subscription.start()
        Thread.sleep(10)
        subscription.pause()

        Thread.sleep(1000)
        subscription.play()
        Thread.sleep(10)
        subscription.cancel()
    }
}