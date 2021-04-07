package api.samples

import api.samples.producerconsumer.processor.impl.SimpleProcessor
import api.samples.producerconsumer.processor.impl.SimpleActorProcessor
import api.samples.producerconsumer.subscription.SimpleSubscription
import api.samples.producerconsumer.subscription.SimpleSubscriptionWithExecutor
import java.util.concurrent.Executors

fun main() {
    //DurableQueueExample().start()
    PubSubExample().start()
    //ActorModeExample().start()
}

class PubSubExample {
    fun start() {
        val executorService = Executors.newFixedThreadPool(1)

        val processor = SimpleProcessor()
        val subscription =  SimpleSubscriptionWithExecutor("TOPIC_1", processor, executorService)

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
        val processor = SimpleProcessor()
        val subscription =  SimpleSubscription("TOPIC_1", processor)

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
        val processor = SimpleActorProcessor()
        val subscription =  SimpleSubscription("TOPIC_1A", processor)

        subscription.start()
        Thread.sleep(10)
        subscription.pause()

        Thread.sleep(1000)
        subscription.play()
        Thread.sleep(10)
        subscription.cancel()
    }
}