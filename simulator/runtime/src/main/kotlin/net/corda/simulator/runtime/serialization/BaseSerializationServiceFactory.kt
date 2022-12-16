package net.corda.simulator.runtime.serialization

import net.corda.simulator.factories.SerializationServiceFactory
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.serialization.SerializationCustomSerializer

class BaseSerializationServiceFactory : SerializationServiceFactory {

    override fun create(customSerializers: List<SerializationCustomSerializer<*, *>>): SerializationService {
        return BaseSerializationService(customSerializers)
    }
}