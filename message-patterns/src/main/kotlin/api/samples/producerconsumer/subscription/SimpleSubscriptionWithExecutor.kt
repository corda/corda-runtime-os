package api.samples.producerconsumer.subscription

import api.samples.producerconsumer.processor.Processor
import java.util.concurrent.ExecutorService

class SimpleSubscriptionWithExecutor(eventSource: String, processor: Processor<String>,
                                     private val executorService: ExecutorService) : SimpleSubscription(eventSource, processor) {

    override fun runProcessLoop() {
        while (!cancelled) {
            if (running) {
                //Some logic to use executor to process from queue in multi-threaded fashion
                executorService.execute(::process)
            }
        }
    }

    override fun cancel() {
        println("Subscription: cancelling subscription $eventSource ....")
        cancelled = true
        running = false
        executorService.shutdownNow()
        processor.onCancel()
    }
}