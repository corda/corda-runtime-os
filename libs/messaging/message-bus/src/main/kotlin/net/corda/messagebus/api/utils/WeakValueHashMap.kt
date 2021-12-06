package net.corda.messagebus.api.utils

import net.corda.v5.base.util.uncheckedCast
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference


/**
 * This Map will remove entries when the value in the map has been
 * cleaned from garbage collection
 */
class WeakValueHashMap<K, V>: MutableMap<K, V> {

    /* Hash table mapping Keys to WeakValues */
    private val map = HashMap<K, WeakValueRef<K, V>>()

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
        var ref: WeakValueRef<K, V>?
        while (queue.poll().also { ref = uncheckedCast(it) } != null) {
            if (ref === map[ref!!.key]) {
                // only remove if it is the *exact* same WeakValueRef
                map.remove(ref!!.key)
            }
        }
    }
}
