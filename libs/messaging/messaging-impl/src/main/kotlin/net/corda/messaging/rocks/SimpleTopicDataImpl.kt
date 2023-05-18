package net.corda.messaging.rocks

import net.corda.messaging.api.subscription.data.DataProcessor
import net.corda.messaging.api.subscription.data.TopicData
import java.util.concurrent.ConcurrentHashMap

class SimpleTopicDataImpl<K: Any, V: Any> (map: ConcurrentHashMap<K, V>? = null) : TopicData<K, V> {

    private var internalMap = map?:ConcurrentHashMap<K, V>()
    override val size: Int
        get() = internalMap.size

    override fun clear() {
        internalMap.clear()
    }

    override fun iterate(dataProcessor: DataProcessor<K, V>) {
        for(entry in internalMap) {
            dataProcessor.process(entry.key, entry.value)
        }
    }

    override fun remove(key: K): V? {
        return internalMap.remove(key)
    }

    override fun put(key: K, value: V) {
        internalMap[key] = value
    }

    override fun get(key: K): V? {
        return internalMap[key]
    }
}
