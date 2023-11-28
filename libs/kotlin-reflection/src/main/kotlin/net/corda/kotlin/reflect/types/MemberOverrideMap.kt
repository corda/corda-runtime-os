package net.corda.kotlin.reflect.types

import java.util.LinkedList
import kotlin.collections.MutableMap.MutableEntry

/**
 * The JVM allows a function to override/implement another
 * function providing all of these conditions is met:
 * - Their names match.
 * - The number, order and types of their parameters match.
 * - The return type of the overriding/implementing function
 *   is assignable to the original function's return type.
 *
 * The looser requirement on the functions' return types means
 * that we cannot identify function overrides by comparing their
 * method descriptors.
 *
 * This [MutableMap] implements the "fuzzier" signature matching
 * logic that we need. The idea is that [members] contains the
 * exact mapping between method/function and [MemberSignature],
 * falling back to a [lookup] of a [MemberSignature] which has
 * a [MemberSignature.isAssignableFrom] relationship.
 */
internal class MemberOverrideMap<V> : MutableMap<MemberSignature, V> {
    private val members = mutableMapOf<MemberSignature, V>()
    private val lookup = mutableMapOf<String, MutableList<MemberSignature>>()

    override val size: Int
        get() = members.size

    override val keys: MutableSet<MemberSignature>
        get() = MemberKeySet(members.keys)

    /**
     * These [values] are not "live", i.e. modifying
     * this [MutableCollection] will not affect any
     * underlying [MutableMap].
     * We do not need "liveness" here, and it would
     * be too expensive to maintain [lookup] without
     * information about [values]' associated keys.
     */
    override val values: MutableCollection<V>
        get() = LinkedList(members.values)

    override val entries: MutableSet<MutableEntry<MemberSignature, V>>
        get() = MemberEntrySet(members.entries)

    override fun put(key: MemberSignature, value: V): V? {
        return members.put(key, value) ?: run {
            val candidates = lookup.computeIfAbsent(key.name) { LinkedList() }
            val overridden = candidates.extractFirstBy { signature ->
                // Sorting the class's subtypes into "assignability" order
                // does not guarantee that their method signatures will be
                // similarly ordered.
                signature.isAssignableFrom(key) || key.isAssignableFrom(signature)
            }
            // Store the most derived signature.
            if (overridden != null && key.isAssignableFrom(overridden)) {
                candidates.add(overridden)
                members.remove(key)
            } else {
                candidates.add(key)
                overridden?.let(members::remove)
            }
        }
    }

    override fun get(key: MemberSignature): V? {
        return members[key] ?: run {
            val candidate = lookup[key.name]?.firstOrNull { signature ->
                // The map contains the most derived signature,
                // so the key will be a supertype.
                key.isAssignableFrom(signature)
            }
            candidate?.let(members::get)
        }
    }

    override fun remove(key: MemberSignature): V? {
        return removeLookup(key)?.let(members::remove)
    }

    override fun containsKey(key: MemberSignature): Boolean = members.containsKey(key)

    override fun containsValue(value: V): Boolean = members.containsValue(value)

    override fun isEmpty(): Boolean = members.isEmpty()

    override fun clear() {
        members.clear()
        lookup.clear()
    }

    override fun putAll(from: Map<out MemberSignature, V>) {
        for (entry in from.entries) {
            put(entry.key, entry.value)
        }
    }

    private fun removeLookup(key: MemberSignature): MemberSignature? {
        return lookup[key.name]?.extractFirstBy { signature ->
            // The map contains the most derived signature,
            // so the key will be a supertype.
            key.isAssignableFrom(signature)
        }
    }

    /**
     * The key set for a [MutableMap] is "live", i.e. removing any
     * key should also remove its corresponding value from the map.
     */
    private inner class MemberKeySet(private val memberKeys: MutableSet<MemberSignature>) : MutableSet<MemberSignature> {
        override val size: Int
            get() = memberKeys.size

        override fun remove(element: MemberSignature): Boolean {
            return this@MemberOverrideMap.remove(element) != null
        }

        override fun removeAll(elements: Collection<MemberSignature>): Boolean {
            var result = false
            elements.forEach { element ->
                result = result or remove(element)
            }
            return result
        }

        override fun iterator(): MutableIterator<MemberSignature> = MemberKeyIterator(memberKeys.iterator())

        override fun isEmpty(): Boolean = memberKeys.isEmpty()

        override fun clear() = this@MemberOverrideMap.clear()

        override fun contains(element: MemberSignature): Boolean = memberKeys.contains(element)

        override fun containsAll(elements: Collection<MemberSignature>): Boolean = memberKeys.containsAll(elements)

        override fun retainAll(elements: Collection<MemberSignature>): Boolean {
            var result = false
            val keyIterator = iterator()
            while (keyIterator.hasNext()) {
                if (keyIterator.next() !in elements) {
                    keyIterator.remove()
                    result = true
                }
            }
            return result
        }

        override fun add(element: MemberSignature): Boolean = throw UnsupportedOperationException(
            "MemberOverrideMap.keys.add() is not supported."
        )

        override fun addAll(elements: Collection<MemberSignature>): Boolean = throw UnsupportedOperationException(
            "MemberOverrideMap.keys.addAll() is not supported."
        )
    }

    private inner class MemberKeyIterator(
        private val keyIterator: MutableIterator<MemberSignature>
    ) : MutableIterator<MemberSignature> {
        private var currentValue: MemberSignature? = null

        override fun hasNext(): Boolean = keyIterator.hasNext()

        override fun next(): MemberSignature {
            return keyIterator.next().also { signature ->
                currentValue = signature
            }
        }

        override fun remove() {
            currentValue?.also(this@MemberOverrideMap::removeLookup)
            keyIterator.remove()
        }
    }

    /**
     * The entries set for a [MutableMap] is "live", i.e. removing any
     * entry should also remove its corresponding entry from the map.
     */
    private inner class MemberEntrySet<V>(
        private val memberEntries: MutableSet<MutableEntry<MemberSignature, V>>
    ) : MutableSet<MutableEntry<MemberSignature, V>> {
        override val size: Int
            get() = memberEntries.size

        override fun isEmpty(): Boolean = memberEntries.isEmpty()
        override fun clear() = this@MemberOverrideMap.clear()

        override fun iterator(): MutableIterator<MutableEntry<MemberSignature, V>> = MemberEntryIterator(memberEntries.iterator())

        override fun contains(element: MutableEntry<MemberSignature, V>): Boolean = memberEntries.contains(element)

        override fun containsAll(elements: Collection<MutableEntry<MemberSignature, V>>): Boolean = memberEntries.containsAll(elements)

        override fun remove(element: MutableEntry<MemberSignature, V>): Boolean {
            return this@MemberOverrideMap.remove(element.key) != null
        }

        override fun removeAll(elements: Collection<MutableEntry<MemberSignature, V>>): Boolean {
            var result = false
            elements.forEach { element ->
                result = result or remove(element)
            }
            return result
        }

        override fun retainAll(elements: Collection<MutableEntry<MemberSignature, V>>): Boolean {
            var result = false
            val keyIterator = iterator()
            while (keyIterator.hasNext()) {
                if (keyIterator.next() !in elements) {
                    keyIterator.remove()
                    result = true
                }
            }
            return result
        }

        override fun add(element: MutableEntry<MemberSignature, V>): Boolean = throw UnsupportedOperationException(
            "MemberOverrideMap.entries.add() is not supported."
        )

        override fun addAll(elements: Collection<MutableEntry<MemberSignature, V>>): Boolean = throw UnsupportedOperationException(
            "MemberOverrideMap.entries.addAll() is not supported."
        )
    }

    private inner class MemberEntryIterator<V>(
        private val entryIterator: MutableIterator<MutableEntry<MemberSignature, V>>
    ) : MutableIterator<MutableEntry<MemberSignature, V>> {
        private var currentValue: MutableEntry<MemberSignature, V>? = null

        override fun hasNext(): Boolean = entryIterator.hasNext()

        override fun next(): MutableEntry<MemberSignature, V> {
            return MemberEntry(entryIterator.next()).also { entry ->
                currentValue = entry
            }
        }

        override fun remove() {
            currentValue?.key?.also(this@MemberOverrideMap::removeLookup)
            entryIterator.remove()
        }
    }

    private class MemberEntry<V>(
        private val entry: MutableEntry<MemberSignature, V>
    ) : MutableEntry<MemberSignature, V> {
        override val key: MemberSignature
            get() = entry.key
        override val value: V
            get() = entry.value

        override fun setValue(newValue: V): V = throw UnsupportedOperationException("MemberOverrideMap.Entry.setValue() is not supported.")
    }
}
