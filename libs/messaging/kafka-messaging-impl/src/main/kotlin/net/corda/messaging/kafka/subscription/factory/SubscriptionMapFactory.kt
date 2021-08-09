package net.corda.messaging.kafka.subscription.factory

interface SubscriptionMapFactory<K : Any, V : Any> {
    /**
     * Returns a new map for use in Message pattern subscriptions. Use [destroyMap]
     * when finished.
     */
    fun createMap(): MutableMap<K, V>

    /**
     * Destroys a map created by [createMap].
     */
    fun destroyMap(map: MutableMap<K, V>)
}



interface StateEventSubscriptionMapFactory<K : Any, S : Any> {
    /**
     * Returns a new map for use in Message pattern subscriptions. Use [destroyMap]
     * when finished.
     */
    fun createMap(): MutableMap<Int, MutableMap<K, Pair<Long, S>>>

    /**
     * Destroys a map created by [createMap].
     */
    fun destroyMap(map: MutableMap<Int, MutableMap<K, Pair<Long, S>>>)
}
