package net.corda.kotlin.reflect.types

internal class UnionMap<K, V>(
    private val firstMap: MutableMap<K, V>,
    private val secondMap: MutableMap<K, V>
) : MutableMap<K, V> {
    override val size: Int
        get() = throw UnsupportedOperationException()

    override fun containsKey(key: K): Boolean {
        return firstMap.containsKey(key) || secondMap.containsKey(key)
    }

    override fun containsValue(value: V): Boolean {
        return firstMap.containsValue(value) || secondMap.containsValue(value)
    }

    override fun get(key: K): V? {
        return firstMap[key] ?: secondMap[key]
    }

    override fun remove(key: K): V? {
        return firstMap.remove(key) ?: secondMap.remove(key)
    }

    override fun isEmpty(): Boolean {
        return firstMap.isEmpty() && secondMap.isEmpty()
    }

    override fun clear() {
        firstMap.clear()
        secondMap.clear()
    }

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = throw UnsupportedOperationException()
    override val keys: MutableSet<K>
        get() = throw UnsupportedOperationException()
    override val values: MutableCollection<V>
        get() = throw UnsupportedOperationException()
    override fun put(key: K, value: V) = throw UnsupportedOperationException()
    override fun putAll(from: Map<out K, V>) = throw UnsupportedOperationException()
}
