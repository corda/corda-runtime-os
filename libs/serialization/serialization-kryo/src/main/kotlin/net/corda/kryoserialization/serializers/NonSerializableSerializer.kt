package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.serialization.checkpoint.NonSerializable

internal object NonSerializableSerializer : Serializer<NonSerializable>() {
    override fun write(kryo: Kryo, output: Output, nonSerializable: NonSerializable) {
        val message = "${nonSerializable.javaClass.name}, has been marked as a non-serializable type is should never" +
                " be serialised into a checkpoint."
        throw UnsupportedOperationException(message)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<out NonSerializable>) =
        throw IllegalStateException("Should not reach here!")
}