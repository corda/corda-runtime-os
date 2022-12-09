package net.corda.simulator.factories

import net.corda.v5.application.serialization.SerializationService
import java.util.ServiceLoader

interface SerializationServiceFactory {
    companion object {
        private val delegate by lazy { ServiceLoader.load(SerializationServiceFactory::class.java).first().create() }

        /**
         * Creates a standalone service, which can be used outside of Simulator for
         * serializing and deserializing large objects and objects which can't be formatted
         * using Json.
         *
         * @return A [SerializationService].
         */
        fun create() : SerializationService {
            return delegate
        }
    }

    /**
     * @return A [SerializationService].
     */
    fun create() : SerializationService
}