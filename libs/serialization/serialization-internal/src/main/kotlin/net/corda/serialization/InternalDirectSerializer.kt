package net.corda.serialization

/**
 * Internal serializer for objects that can be serialized as a single
 * AMQP primitive, such as [ByteArray], [String] or [UUID][java.util.UUID].
 * Use an [InternalProxySerializer] to serialize more complex types.
 */
interface InternalDirectSerializer<OBJ : Any> : InternalCustomSerializer<OBJ> {
    fun readObject(reader: ReadObject, context: SerializationContext): OBJ
    fun writeObject(obj: OBJ, writer: WriteObject, context: SerializationContext)

    interface ReadObject {
        fun getAsBytes(): ByteArray
        fun <T : Any> getAs(type: Class<T>): T
    }

    interface WriteObject {
        fun putAsBytes(value: ByteArray)
        fun putAsString(value: String)
        fun putAsObject(value: Any)
    }
}
