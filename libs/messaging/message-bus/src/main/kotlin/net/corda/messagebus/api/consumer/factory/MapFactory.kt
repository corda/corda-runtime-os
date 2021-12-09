package net.corda.messagebus.api.consumer.factory

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

object DEFAULT_MAP_FACTORY : MapFactory<Any, Any> {
    override fun createMap(): MutableMap<Any, Any> = createMap()
    override fun destroyMap(map: MutableMap<Any, Any>) = map.clear()
}

