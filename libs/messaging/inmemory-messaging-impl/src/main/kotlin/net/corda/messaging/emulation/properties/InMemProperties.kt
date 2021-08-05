package net.corda.messaging.emulation.properties

class InMemProperties {
    companion object {
        const val TOPICS_MAX_SIZE = "topics.max.size"
        const val TOPICS_POLL_SIZE = "topics.poll.size"
        const val PARTITION_SIZE = "topics.partition.size"
        const val CONSUMER_THREAD_STOP_TIMEOUT = "subscription.consumer.thread.stop.timeout"
    }
}
