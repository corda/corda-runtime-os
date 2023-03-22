package net.corda.interop.data

interface JsonMarshaller {

    fun serialize(value: Any): String
    fun <T : Any> deserialize(value: String, type: Class<T>): T

}