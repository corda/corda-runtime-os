package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.KryoException
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.kryoserialization.serializationContext
import net.corda.utilities.castIfPossible
import net.corda.v5.serialization.SerializationToken
import net.corda.v5.serialization.SerializeAsToken
import net.corda.v5.serialization.SingletonSerializeAsToken

/**
 * A Kryo serializer for [SerializeAsToken] implementations.
 */
internal object SingletonSerializeAsTokenSerializer : Serializer<SingletonSerializeAsToken>() {
    override fun write(kryo: Kryo, output: Output, obj: SingletonSerializeAsToken) {
        val token = kryo.serializationContext()?.toSingletonToken(obj)
            ?: throw KryoException("Attempt to write a ${SingletonSerializeAsToken::class.simpleName} instance " +
                    "of ${obj.javaClass.name} without initialising a context")
        kryo.writeClassAndObject(output, token)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<SingletonSerializeAsToken>): SingletonSerializeAsToken {
        val token = (kryo.readClassAndObject(input) as? SerializationToken)
            ?: throw KryoException("Non-token read for tokenized type: ${type.name}")
        val fromToken = token.fromToken(
            kryo.serializationContext()
                ?: throw KryoException("Attempt to read a token for a ${SerializeAsToken::class.simpleName} instance " +
                        "of ${type.name} without initialising a context")
        )
        return type.castIfPossible(fromToken)
            ?: throw KryoException("Token read ($token) did not return expected tokenized type: ${type.name}")
    }
}