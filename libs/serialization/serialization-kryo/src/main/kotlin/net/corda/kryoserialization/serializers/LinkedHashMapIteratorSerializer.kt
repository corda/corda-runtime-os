package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import java.lang.reflect.Field

/**
 * The [LinkedHashMap] and [LinkedHashSet] have a problem with the default Quasar/Kryo serialisation
 * in that serialising an iterator (and subsequent [LinkedHashMap.Entry]) over a sufficiently large
 * data set can lead to a stack overflow (because the object map is traversed recursively).
 *
 * We've added our own custom serializer in order to ensure that the iterator is correctly deserialized.
 */
internal object LinkedHashMapIteratorSerializer : Serializer<Iterator<*>>() {
    private val DUMMY_MAP = linkedMapOf(1L to 1)
    private val outerMapField: Field = getIterator()::class.java.superclass.getDeclaredField("this$0").apply { isAccessible = true }
    private val currentField: Field = getIterator()::class.java.superclass.getDeclaredField("current").apply { isAccessible = true }

    private val KEY_ITERATOR_CLASS: Class<MutableIterator<Long>> = DUMMY_MAP.keys.iterator().javaClass
    private val VALUE_ITERATOR_CLASS: Class<MutableIterator<Int>> = DUMMY_MAP.values.iterator().javaClass
    private val MAP_ITERATOR_CLASS: Class<MutableIterator<MutableMap.MutableEntry<Long, Int>>> = DUMMY_MAP.iterator().javaClass

    fun getIterator(): Any = DUMMY_MAP.iterator()

    override fun write(kryo: Kryo, output: Output, obj: Iterator<*>) {
        val current: Map.Entry<*, *>? = currentField.get(obj) as Map.Entry<*, *>?
        kryo.writeClassAndObject(output, outerMapField.get(obj))
        kryo.writeClassAndObject(output, current)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<Iterator<*>>): Iterator<*> {
        val outerMap = kryo.readClassAndObject(input) as Map<*, *>
        return when (type) {
            KEY_ITERATOR_CLASS -> {
                val current = (kryo.readClassAndObject(input) as? Map.Entry<*, *>)?.key
                outerMap.keys.iterator().returnToIteratorLocation(kryo, current)
            }
            VALUE_ITERATOR_CLASS -> {
                val current = (kryo.readClassAndObject(input) as? Map.Entry<*, *>)?.value
                outerMap.values.iterator().returnToIteratorLocation(kryo, current)
            }
            MAP_ITERATOR_CLASS -> {
                val current = (kryo.readClassAndObject(input) as? Map.Entry<*, *>)
                outerMap.iterator().returnToIteratorLocation(kryo, current)
            }
            else -> throw IllegalStateException("Invalid type")
        }
    }

    private fun Iterator<*>.returnToIteratorLocation(kryo: Kryo, current: Any?): Iterator<*> {
        while (this.hasNext()) {
            val key = this.next()
            if (iteratedObjectsEqual(kryo, key, current)) break
        }
        return this
    }

    private fun iteratedObjectsEqual(kryo: Kryo, a: Any?, b: Any?): Boolean = if (a == null || b == null) {
        a == b
    } else {
        a === b || mapEntriesEqual(kryo, a, b) || kryoOptimisesAwayReferencesButEqual(kryo, a, b)
    }

    /**
     * Kryo can substitute brand new created instances for some types during deserialization, making the identity check fail.
     * Fall back to equality for those.
     */
    private fun kryoOptimisesAwayReferencesButEqual(kryo: Kryo, a: Any, b: Any) =
            (!kryo.referenceResolver.useReferences(a.javaClass) && !kryo.referenceResolver.useReferences(b.javaClass) && a == b)

    private fun mapEntriesEqual(kryo: Kryo, a: Any, b: Any) =
            (a is Map.Entry<*, *> && b is Map.Entry<*, *> && iteratedObjectsEqual(kryo, a.key, b.key))
}
