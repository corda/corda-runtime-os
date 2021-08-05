package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.internal.base.LazyMappedList
import net.corda.v5.base.types.NonEmptySet
import net.corda.v5.base.util.toNonEmptySet

/* This file contains Serializers for types which are defined in the Base module.
 * We do this here as we don't want Base to depend on serialization-internal.
*/

internal object NonEmptySetSerializer : Serializer<NonEmptySet<Any>>() {
    override fun write(kryo: Kryo, output: Output, obj: NonEmptySet<Any>) {
        // Write out the contents as normal
        output.writeInt(obj.size, true)
        obj.forEach { kryo.writeClassAndObject(output, it) }
    }

    override fun read(kryo: Kryo, input: Input, type: Class<NonEmptySet<Any>>): NonEmptySet<Any> {
        val size = input.readInt(true)
        require(size >= 1) { "Invalid size read off the wire: $size" }
        val list = ArrayList<Any>(size)
        repeat(size) {
            list += kryo.readClassAndObject(input)
        }
        return list.toNonEmptySet()
    }
}

/** For serializing the utility [LazyMappedList]. It will serialize the fully resolved object.*/
@SuppressWarnings("ALL")
object LazyMappedListSerializer : Serializer<List<*>>() {
    override fun write(kryo: Kryo, output: Output, obj: List<*>) = kryo.writeClassAndObject(output, obj.toList())
    override fun read(kryo: Kryo, input: Input, type: Class<List<*>>) = kryo.readClassAndObject(input) as List<*>
}