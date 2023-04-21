package net.corda.simulator.factories

import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.serialization.SerializationCustomSerializer
import java.util.ServiceLoader

interface SerializationServiceFactory {

    companion object {
        private fun delegate(customSerializers: List<SerializationCustomSerializer<*, *>>): SerializationService{
            return ServiceLoader.load(SerializationServiceFactory::class.java).first().create(customSerializers)
        }

        /**
         * Creates a standalone service, which can be used outside of Simulator for
         * serializing and deserializing large objects and objects which can't be formatted
         * using Json.
         *
         * @param customSerializers A list of custom serializers
         * @return A [SerializationService].
         */
        fun create(customSerializers: List<SerializationCustomSerializer<*, *>> = emptyList()) : SerializationService {
            return delegate(customSerializers)
        }
    }

    /**
     * @param customSerializers A list of custom serializers
     * @return A [SerializationService].
     */
    fun create(customSerializers: List<SerializationCustomSerializer<*, *>> = emptyList()) : SerializationService
}