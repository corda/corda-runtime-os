package net.corda.schema.registry

import net.corda.data.Fingerprint
import org.apache.avro.Schema
import java.nio.ByteBuffer

/**
 * Contains the Avro-based Schemas which can be (de)serialized as well as the logic to implement the (de)serialization.
 *
 * The format of the serialized bytes will be
 * |------+------------+------+------------------|
 *  MAGIC  Fingerprint  Flags  Payload
 *
 *  The fields are defined as:
 *  MAGIC - A constant value which can be used to determine the version of the serialization protocol.
 *  Fingerprint - The unique ID of the schema to which the object was encoded by.
 *  Flags - The set of flags that define characteristics (ie compression) of the payload
 *  Payload - The serialized bytes (possibly compressed) of the object.
 */
interface AvroSchemaRegistry {

    /**
     * Adds a new [schema] to this registry, which can be used for (de)serialization.
     *
     * @param schema The (Avro) [Schema].  This will be the schema used to encode/decode objects of type [clazz].
     * @param clazz The class type which [schema] will map to.
     * @param encoder Defines the encoding process from object to serialized byte array.
     * @param decoder Defines the decoding process from serialized byte array to object.
     */
    fun <T: Any> addSchema(
        schema: Schema,
        clazz: Class<T>,
        encoder: (T)-> ByteArray,
        decoder: (ByteArray, Schema, T?) -> T
    )

    /**
     * Adds a new [schema] to this registry, without the explicit encoder/decoder.
     *
     * This may be necessary when, for example, a new schema for a known type is added (as in object evolution).
     * The given schema can then be used to determine how the object was serialized and, therefore, how to read
     * the bytes on deserialization.
     *
     * @param schema the schema which will be added.
     */
    fun addSchemaOnly(
        schema: Schema
    )

    /**
     * Serialize the given [obj] into a byte buffer.
     *
     * @param obj the object to be serialized.
     * @return a buffer of the serialized bytes.
     */
    fun <T: Any> serialize(obj: T) : ByteBuffer

    /**
     * Deserialize the [bytes] into the expected object
     *
     * @param bytes a buffer of the serialized bytes to be decoded.
     * @param clazz the class type of the returned decoded object.
     * @param reusable if not null, an object into which the decoded [bytes] will be written to.  If null, then
     * an object of type [clazz] will be created.
     * @return the deserialized object encoded in [bytes].
     */
    fun <T: Any> deserialize(bytes: ByteBuffer, clazz: Class<T>, reusable: T?): T

    /**
     * Deserialize the [bytes] into the expected object
     *
     * @param bytes a buffer of the serialized bytes to be decoded.
     * @param offset an offset into [bytes] from which to start decoding.
     * @param length the number of bytes (from [offset]) which will be decoded.
     * @param clazz the class type of the returned decoded object.
     * @param reusable if not null, an object into which the decoded [bytes] will be written to.  If null, then
     * an object of type [clazz] will be created.
     * @return the deserialized object encoded in [bytes].
     */
    fun <T: Any> deserialize(bytes: ByteBuffer, offset: Int, length: Int, clazz: Class<T>, reusable: T?): T

    /**
     * Inspects the buffer to determine the class type contained in the envelope.
     *
     * @param bytes a buffer of a serialized object.
     * @return the class type of the object encoded in [bytes].
     */
    fun getClassType(bytes: ByteBuffer) : Class<*>

    val schemasByFingerprintSnapshot: Map<Fingerprint, Schema>
}

/**
 * Extension function to simplify development in Kotlin.
 */
inline fun <reified T: Any> AvroSchemaRegistry.deserialize(bytes: ByteBuffer, reusable: T? = null): T =
    deserialize(bytes, T::class.java, reusable)

/**
 * Extension function to simplify development in Kotlin.
 */
inline fun <reified T: Any> AvroSchemaRegistry.deserialize(bytes: ByteBuffer, offset:Int, length: Int, reusable: T? = null): T =
    deserialize(bytes, offset, length, T::class.java, reusable)