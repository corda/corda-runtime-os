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

    private val outerMapField = serializedType.getDeclaredField("this$0").apply {
        isAccessible = true
    }

    override fun write(kryo: Kryo, output: Output?, obj: Set<*>) {
        kryo.writeClassAndObject(output, outerMapField.get(obj))
    }

    override fun read(kryo: Kryo, input: Input?, type: Class<out Set<*>>): Set<*> {
        val map = kryo.readClassAndObject(input) as Map<*, *>
        return map.keys
    }
}
