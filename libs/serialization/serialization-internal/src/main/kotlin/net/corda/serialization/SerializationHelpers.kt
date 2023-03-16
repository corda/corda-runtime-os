package net.corda.serialization

import net.corda.v5.base.types.ByteArrays.sequence
import net.corda.v5.base.types.ByteSequence
import net.corda.v5.serialization.SerializedBytes
import java.sql.Blob

/**
 * Convenience extension method for deserializing a ByteSequence, utilising the defaults.
 */
inline fun <reified T : Any> ByteSequence.deserialize(serializationFactory: SerializationFactory,
                                                      context: SerializationContext): T {
    return serializationFactory.deserialize(this, T::class.java, context)
}

/**
 * Additionally returns [SerializationContext] which was used for encoding.
 * It might be helpful to know [SerializationContext] to use the same encoding in the reply.
 */
inline fun <reified T : Any> ByteSequence.deserializeWithCompatibleContext(serializationFactory: SerializationFactory,
                                                                           context: SerializationContext): ObjectWithCompatibleContext<T> {
    return serializationFactory.deserializeWithCompatibleContext(this, T::class.java, context)
}

/**
 * Convenience extension method for deserializing SerializedBytes with type matching, utilising the defaults.
 */
inline fun <reified T : Any> SerializedBytes<T>.deserialize(serializationFactory: SerializationFactory,
                                                            context: SerializationContext): T {
    return serializationFactory.deserialize(this, T::class.java, context)
}

/**
 * Convenience extension method for deserializing a ByteArray, utilising the defaults.
 */
inline fun <reified T : Any> ByteArray.deserialize(serializationFactory: SerializationFactory,
                                                   context: SerializationContext): T {
    require(isNotEmpty()) { "Empty bytes" }
    return sequence(this).deserialize(serializationFactory, context)
}

/**
 * Convenience extension method for deserializing a JDBC Blob, utilising the defaults.
 */
inline fun <reified T : Any> Blob.deserialize(serializationFactory: SerializationFactory,
                                              context: SerializationContext): T {
    return this.getBytes(1, this.length().toInt()).deserialize(serializationFactory, context)
}

/**
 * Convenience extension method for serializing an object of type T, utilising the defaults.
 */
fun <T : Any> T.serialize(serializationFactory: SerializationFactory,
                          context: SerializationContext): SerializedBytes<T> {
    return serializationFactory.serialize(this, context)
}
