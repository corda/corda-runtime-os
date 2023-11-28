package net.corda.avro.serialization

/**
 * Defines the interface for message bus serialization. The underlying mechanism may differ.
 */
interface CordaAvroSerializer<T> {

    /**
     * Serialize the {@code data} into a {@code byte[]}.
     *
     * @param data The object to be serialized.
     * @return The serialized byte stream for transfer across the message bus or null if unsuccessful.
     */
    fun serialize(data: T): ByteArray?
}
