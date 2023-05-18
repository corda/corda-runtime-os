package net.corda.messaging.api.subscription.data

/**
 * Client supplied processor used to iterate through a [TopicData] class
 */
fun interface DataProcessor<K : Any, V : Any> {
    fun process(key: K, value: V)
}
