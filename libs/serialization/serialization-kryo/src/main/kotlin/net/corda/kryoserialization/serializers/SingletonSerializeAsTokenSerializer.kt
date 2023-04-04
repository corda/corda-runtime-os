package net.corda.kryoserialization.serializers

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import net.corda.kryoserialization.CordaKryoException
import net.corda.v5.serialization.SingletonSerializeAsToken

/**
 * A Kryo serializer for [SingletonSerializeAsToken] implementations.
 */
class SingletonSerializeAsTokenSerializer(
    private val serializableInstances: Map<String, SingletonSerializeAsToken>,
) : Serializer<SingletonSerializeAsToken>() {
    @Suppress("unused")
    override fun write(kryo: Kryo, output: Output, obj: SingletonSerializeAsToken) {
        if (!serializableInstances.contains(obj.tokenName)) {
            throw CordaKryoException("No instance of type ${obj.tokenName} found in ${this::class.java.simpleName}.")
        }
        kryo.writeClassAndObject(output, obj.tokenName)
    }

    override fun read(kryo: Kryo, input: Input, type: Class<out SingletonSerializeAsToken>): SingletonSerializeAsToken {
        val token = (kryo.readClassAndObject(input) as? String)
            ?: throw CordaKryoException("Attempt to deserialize a tokenized type: ${type.name}, but no token found.")
        return serializableInstances[token]
                ?: throw CordaKryoException("No instance of type ${type.simpleName} found in ${this::class.java.simpleName}.")
    }
}
