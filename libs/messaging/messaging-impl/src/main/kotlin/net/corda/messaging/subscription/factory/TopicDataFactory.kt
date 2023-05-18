package net.corda.messaging.subscription.factory

import net.corda.messaging.api.subscription.data.TopicData

/**
 * Simple interface allowing custom creation/deletion of maps
 */
interface TopicDataFactory<K : Any, V : Any> {
    /**
     * Returns a new map. Use [destroy] when finished.
     */
    fun create(): TopicData<K, V>

    /**
     * Destroys a map created by [create].
     */
    fun destroy(topicData: TopicData<K, V>)
}


/**
 * Simple interface allowing custom creation/deletion of maps
 */
interface MapFactory<K : Any, V : Any> {
    /**
     * Returns a new map. Use [destroyMap] when finished.
     */
    fun createMap(): MutableMap<K, V>

    /**
     * Destroys a map created by [createMap].
     */
    fun destroyMap(map: MutableMap<K, V>)
}
