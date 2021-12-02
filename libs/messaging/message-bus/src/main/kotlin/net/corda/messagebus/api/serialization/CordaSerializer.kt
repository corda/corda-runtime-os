package net.corda.messagebus.api.serialization

interface CordaSerializer<T> {
    fun serialize(topic: String?, data: T?): ByteArray?
}
