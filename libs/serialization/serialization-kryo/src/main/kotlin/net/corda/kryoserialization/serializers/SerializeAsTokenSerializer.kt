package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.KryoException
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.v5.serialization.SerializationToken
import net.corda.v5.serialization.SerializeAsToken
import net.corda.kryoserialization.serializationContext
import net.corda.internal.base.castIfPossible

/**
 * A Kryo serializer for [SerializeAsToken] implementations.
 */
internal class SerializeAsTokenSerializer<T : SerializeAsToken> : Serializer<T>() {
    override fun write(kryo: Kryo, output: Output, obj: T) {
        kryo.writeClassAndObject(output, obj.toToken(kryo.serializationContext()
                ?: throw KryoException("Attempt to write a ${SerializeAsToken::class.simpleName} instance " +
                        "of ${obj.javaClass.name} without initialising a context")))
    }

    override fun read(kryo: Kryo, input: Input, type: Class<T>): T {
        val token = (kryo.readClassAndObject(input) as? SerializationToken)
                ?: throw KryoException("Non-token read for tokenized type: ${type.name}")
        val fromToken = token.fromToken(kryo.serializationContext()
                ?: throw KryoException("Attempt to read a token for a ${SerializeAsToken::class.simpleName} instance " +
                        "of ${type.name} without initialising a context"))
        return type.castIfPossible(fromToken)
                ?: throw KryoException("Token read ($token) did not return expected tokenized type: ${type.name}")
    }
}