package net.corda.messaging.api.subscription.data

/**
 * represents the data on a topic
 */
interface TopicData<K : Any, V: Any> {
    val size: Int

    fun get(key: K): V?

    fun put(key: K, value: V)

    fun remove(key: K): V?

    fun clear()

    fun iterate(dataProcessor: DataProcessor<K, V>)
}
