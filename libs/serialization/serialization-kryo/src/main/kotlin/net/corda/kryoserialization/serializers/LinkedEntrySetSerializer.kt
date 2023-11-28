package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output

/**
 * The [java.util.LinkedHashMap.LinkedEntrySet] has a problem when the default Kryo is
 * deserializing immutable collections: an exception is thrown due to the fact that
 * the methods add() and addAll() were not properly implemented (Take look at the
 * public interface Map<K, V> method to see that it does not support these operations).
 */
internal object LinkedEntrySetSerializer : Serializer<Set<Map.Entry<*, *>>>() {

    // Create a dummy object to get the LinkedHashMap$LinkedEntrySet from it
    val serializedType: Class<out Set<Map.Entry<*, *>>> = linkedMapOf(Any() to Any(), Any() to Any()).entries::class.java
    override fun write(kryo: Kryo, output: Output?, obj: Set<Map.Entry<*, *>>) {
        // HashSet is already supported
        kryo.writeClassAndObject(output, obj.toList())
    }

    override fun read(kryo: Kryo, input: Input?, type: Class<out Set<Map.Entry<*, *>>>?): Set<Map.Entry<*, *>> {
        /*
        In Kotlin, casting a generic type to another with different generic parameters will raise a warning.
        The exception is if the cast lies within variance bounds: https://kotlinlang.org/docs/generics.html#variance
         */
        @Suppress("UNCHECKED_CAST")
        val deserializedList = kryo.readClassAndObject(input) as List<Map.Entry<*, *>>

        // Grant that the return is a LinkedEntrySet
        val collectionMap = deserializedList.associateBy({ it.key }, { it.value }) as Map<*, *>
        return collectionMap.entries
    }
}
