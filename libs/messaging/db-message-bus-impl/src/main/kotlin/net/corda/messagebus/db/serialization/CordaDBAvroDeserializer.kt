package net.corda.messagebus.db.serialization

interface CordaDBAvroDeserializer<T> {
    /**
     * Deserialize the given `data` into an object of type `T`.
     *
     * @param data the serialized byte stream representing the data
     * @param topic the topic the data is read from if known
     * @return the object represented by `data` or `null` if unsuccessful
     */
    fun deserialize(data: ByteArray, topic: String?): T?
}
