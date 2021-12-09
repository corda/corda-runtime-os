package net.corda.data

/**
 * Defines the interface for Message Bus serialization.  The underlying mechanism may differ.
 */
interface CordaAvroSerializer<T : Any> {
    /**
     * Serialize the [data] into a [ByteArray]
     *
     * @param data the object to be serialized
     * @return the serialized byte stream for transfer across the message bus or null if unsuccessful
     */
    fun serialize(data: T): ByteArray?
}
