package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.utilities.LazyMappedList

/* This file contains Serializers for types which are defined in the Base module.
 * We do this here as we don't want Base to depend on serialization-internal.
*/

/** For serializing the utility [LazyMappedList]. It will serialize the fully resolved object.*/
@SuppressWarnings("ALL")
object LazyMappedListSerializer : Serializer<List<*>>() {
    override fun write(kryo: Kryo, output: Output, obj: List<*>) = kryo.writeClassAndObject(output, obj.toList())
    override fun read(kryo: Kryo, input: Input, type: Class<out List<*>>) = kryo.readClassAndObject(input) as List<*>
}