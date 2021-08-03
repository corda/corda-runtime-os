package net.corda.messaging.emulation.properties

import com.typesafe.config.Config
import com.typesafe.config.ConfigException

class InMemProperties {
    companion object {
        const val TOPICS_MAX_SIZE = "topics.max.size"
        const val TOPICS_POLL_SIZE = "topics.poll.size"
        const val PARTITION_SIZE = "topics.partition.size"
        const val CONSUMER_THREAD_STOP_TIMEOUT = "subscription.consumer.thread.stop.timeout"
        const val DEFAULT_PARTITION_SIZE = 10
        const val DEFAULT_POLL_SIZE = 5
    }
}
fun Config.getIntOrDefault(path: String, default: Int): Int {
    return try {
        this.getInt(path)
    } catch (e: ConfigException) {
        default
    }
}
