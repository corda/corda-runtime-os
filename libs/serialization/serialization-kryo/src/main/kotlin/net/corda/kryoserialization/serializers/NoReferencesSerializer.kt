package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.kryoserialization.withoutReferences

// Make this public and part of the serializers that get registered with us
class NoReferencesSerializer<T>(private val baseSerializer: Serializer<T>) : Serializer<T>() {

    override fun read(kryo: Kryo, input: Input, type: Class<T>): T {
        return kryo.withoutReferences { baseSerializer.read(kryo, input, type) }
    }

    override fun write(kryo: Kryo, output: Output, obj: T) {
        kryo.withoutReferences { baseSerializer.write(kryo, output, obj) }
    }
}
