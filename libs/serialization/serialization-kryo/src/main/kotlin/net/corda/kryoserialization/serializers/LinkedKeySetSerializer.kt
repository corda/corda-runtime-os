package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import java.util.LinkedHashMap

/**
 * java.util.LinkedHashMap$LinkedKeySet has a serialization problem where the pointer back to the outer map appears to
 * be broken in some circumstances. It also may fail to deserialize for similar reasons to the LinkedEntrySet. This
 * custom serializer aims to address both issues.
 */
internal object LinkedKeySetSerializer : Serializer<Set<*>>() {
    val serializedType: Class<out Set<*>> = LinkedHashMap<Any, Any>().keys::class.java

    override fun write(kryo: Kryo, output: Output?, obj: Set<*>) {
        kryo.writeClassAndObject(output, obj.toList())
    }

    override fun read(kryo: Kryo, input: Input?, type: Class<out Set<*>>): Set<*> {
        @Suppress("UNCHECKED_CAST")
        val deserializedList = kryo.readClassAndObject(input) as List<*>

        // Grant that the return is a LinkedKeySet
        val collectionMap = deserializedList.associateBy({ it }, { Any() }) as Map<*, *>
        return collectionMap.entries
    }
}