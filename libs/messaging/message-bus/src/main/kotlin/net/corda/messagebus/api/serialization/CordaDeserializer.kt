package net.corda.messagebus.api.serialization

interface CordaDeserializer<T> {
    fun deserialize(topic: String, data: ByteArray?): T?
}
