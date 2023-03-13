package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output

// Trivial Serializer which simply returns the given instance, which we already know is a Kotlin object
class KotlinObjectSerializer(private val objectInstance: Any) : Serializer<Any>() {
    override fun read(kryo: Kryo, input: Input, type: Class<out Any>): Any = objectInstance
    override fun write(kryo: Kryo, output: Output, obj: Any) = Unit
}
