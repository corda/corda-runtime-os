package net.corda.messaging.utils

import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap


/**
 * This Map will remove entries when the value in the map has been
 * cleaned from garbage collection
 */
class WeakValueHashMap<K, V>: MutableMap<K, V> {

    /* Mutable map of Keys to WeakValues */
    private val map: MutableMap<K, WeakValueRef<K, V>> = ConcurrentHashMap()

    /* Reference queue for cleared WeakValues */
    private val queue = ReferenceQueue<V>()

    private class WeakValueRef<K, V> private constructor(val key: K, value: V, queue: ReferenceQueue<V>) :
        WeakReference<V>(value, queue) {

        companion object {
            internal fun <K, V> create(key: K, value: V, queue: ReferenceQueue<V>): WeakValueRef<K, V> {
                return WeakValueRef(key, value, queue)
            }
        }
    }

    override fun put(key: K, value: V): V? {
        processQueue()
        val reference = WeakValueRef.create(key, value, queue)
        return map.put(key, reference)?.get()
    }

    override fun isEmpty(): Boolean {
        processQueue()
        return map.isEmpty()
    }

    override val size: Int
        get() {
            processQueue()
            return map.size
        }

    override fun clear() {
        processQueue()
        map.clear()
    }

    override fun containsKey(key: K): Boolean {
        processQueue()
        return map.containsKey(key)
    }

    override fun containsValue(value: V): Boolean {
        throw UnsupportedOperationException()
    }

    override fun get(key: K): V? {
        processQueue()
        val ref = map[key]
        return ref?.get()
    }

    override fun remove(key: K): V? {
        processQueue()
        val result = map.remove(key)
        return result?.get()
    }

    override fun putAll(from: Map<out K, V>) {
        throw UnsupportedOperationException()
    }

    override val keys: MutableSet<K>
        get() = map.keys
    override val values: MutableCollection<V>
        get() = throw UnsupportedOperationException()
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = throw UnsupportedOperationException()

    /* Remove all invalidated entries from the map, that is, remove all entries
       whose values have been discarded.
     */
    private fun processQueue() {
        while (true) {
            val ref = (queue.poll() as? WeakValueRef<*,*>) ?: break
            // only remove if it is the *exact* same WeakValueRef
            map.remove(ref.key, ref)
        }
    }
}
