package net.corda.internal.serialization.amqp

interface SerializerFor {
    /**
     * This method should return true if the custom serializer can serialize an instance of the class passed as the
     * parameter.
     */
    fun isSerializerFor(clazz: Class<*>): Boolean
}