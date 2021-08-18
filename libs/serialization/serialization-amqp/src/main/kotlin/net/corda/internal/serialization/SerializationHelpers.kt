package net.corda.internal.serialization

import net.corda.v5.base.types.ByteSequence
import net.corda.v5.base.types.sequence
import net.corda.v5.serialization.ObjectWithCompatibleContext
import net.corda.v5.serialization.SerializationContext
import net.corda.v5.serialization.SerializationFactory
import net.corda.v5.serialization.SerializedBytes
import java.sql.Blob

/**
 * Convenience extension method for deserializing a ByteSequence, utilising the defaults.
 */
inline fun <reified T : Any> ByteSequence.deserialize(serializationFactory: SerializationFactory = SerializationDefaults.currentOrDefaultFactory,
                                                      context: SerializationContext = SerializationDefaults.currentOrDefaultContext(serializationFactory)): T {
    return serializationFactory.deserialize(this, T::class.java, context)
}

/**
 * Additionally returns [SerializationContext] which was used for encoding.
 * It might be helpful to know [SerializationContext] to use the same encoding in the reply.
 */
inline fun <reified T : Any> ByteSequence.deserializeWithCompatibleContext(serializationFactory: SerializationFactory = SerializationDefaults.currentOrDefaultFactory,
                                                                           context: SerializationContext = SerializationDefaults.currentOrDefaultContext(serializationFactory)): ObjectWithCompatibleContext<T> {
    return serializationFactory.deserializeWithCompatibleContext(this, T::class.java, context)
}

/**
 * Convenience extension method for deserializing SerializedBytes with type matching, utilising the defaults.
 */
inline fun <reified T : Any> SerializedBytes<T>.deserialize(serializationFactory: SerializationFactory = SerializationDefaults.currentOrDefaultFactory,
                                                            context: SerializationContext = SerializationDefaults.currentOrDefaultContext(serializationFactory)): T {
    return serializationFactory.deserialize(this, T::class.java, context)
}

/**
 * Convenience extension method for deserializing a ByteArray, utilising the defaults.
 */
inline fun <reified T : Any> ByteArray.deserialize(serializationFactory: SerializationFactory = SerializationDefaults.currentOrDefaultFactory,
                                                   context: SerializationContext = SerializationDefaults.currentOrDefaultContext(serializationFactory)): T {
    require(isNotEmpty()) { "Empty bytes" }
    return this.sequence().deserialize(serializationFactory, context)
}

/**
 * Convenience extension method for deserializing a JDBC Blob, utilising the defaults.
 */
inline fun <reified T : Any> Blob.deserialize(serializationFactory: SerializationFactory = SerializationDefaults.currentOrDefaultFactory,
                                              context: SerializationContext = SerializationDefaults.currentOrDefaultContext(serializationFactory)): T {
    return this.getBytes(1, this.length().toInt()).deserialize(serializationFactory, context)
}

/**
 * Convenience extension method for serializing an object of type T, utilising the defaults.
 */
fun <T : Any> T.serialize(serializationFactory: SerializationFactory = SerializationDefaults.currentOrDefaultFactory,
                          context: SerializationContext = SerializationDefaults.currentOrDefaultContext(serializationFactory)): SerializedBytes<T> {
    return serializationFactory.serialize(this, context)
}