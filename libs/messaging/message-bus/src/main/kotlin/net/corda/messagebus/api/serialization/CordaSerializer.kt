package net.corda.messagebus.api.serialization

interface CordaSerializer<T> {
    fun serialize(data: T?): ByteArray?
}
