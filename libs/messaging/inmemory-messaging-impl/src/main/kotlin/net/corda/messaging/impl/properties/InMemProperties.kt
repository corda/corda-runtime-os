package net.corda.messaging.impl.properties

class InMemProperties {
    companion object {
        const val TOPICS_MAX_SIZE = "topics.max.size"
        const val TOPICS_POLL_SIZE = "topics.poll.size"
        const val CONSUMER_THREAD_STOP_TIMEOUT = "subscription.consumer.thread.stop.timeout"
        const val CONSUMER_THREAD_POLL_INTERVAL = "subscription.consumer.thread.poll.interval"
    }
}
