package net.corda.libs.permissions.cache.impl.processor

import java.util.concurrent.ConcurrentHashMap
import net.corda.libs.permissions.cache.processor.PermissionCacheTopicProcessor
import net.corda.messaging.api.records.Record

/**
 * Permission topic processor responsible for updating a permission topic.
 *
 * @param keyClass the type of keys in the cache map.
 * @param valueClass the type of values in the cache map.
 * @param data the ConcurrentHashMap holding the data being maintained by this processor.
 * @param onSnapshotCallback callback executed when snapshot is complete.
 */
class PermissionTopicProcessor<K : Any, V : Any>(
    override val keyClass: Class<K>,
    override val valueClass: Class<V>,
    private val data: ConcurrentHashMap<K, V>,
    private val onSnapshotCallback: () -> Unit,
) : PermissionCacheTopicProcessor<K, V> {

    override fun onSnapshot(currentData: Map<K, V>) {
        data.putAll(currentData)
        onSnapshotCallback.invoke()
    }

    override fun onNext(
        newRecord: Record<K, V>,
        oldValue: V?,
        currentData: Map<K, V>
    ) {
        val value = newRecord.value
        val key = newRecord.key

        if (value == null) {
            data.remove(key)
        } else {
            data[key] = value
        }
    }
}