package net.corda.messaging.emulation.subscription.stateandevent

import net.corda.messaging.api.subscription.data.DataProcessor
import net.corda.messaging.api.subscription.data.TopicData

class TopicDataImpl<K: Any, V: Any> (map: MutableMap<K, V>? = null): TopicData<K, V> {

    private var internalMap = map?: mutableMapOf()
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
