package net.corda.messaging.rocks

import java.util.concurrent.ConcurrentHashMap
import net.corda.messaging.subscription.factory.MapFactory

class SimpleMapFactoryImpl<K: Any, V: Any> : MapFactory<K, V> {
    override fun createMap(): MutableMap<K, V> {
        return ConcurrentHashMap<K, V>()
    }

    override fun destroyMap(map: MutableMap<K, V>) {
        map.clear()
    }
}