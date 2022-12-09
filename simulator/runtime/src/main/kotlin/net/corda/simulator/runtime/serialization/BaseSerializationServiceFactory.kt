package net.corda.simulator.runtime.serialization

import net.corda.simulator.factories.SerializationServiceFactory
import net.corda.v5.application.serialization.SerializationService

class BaseSerializationServiceFactory : SerializationServiceFactory {
    override fun create(): SerializationService {
        return BaseSerializationService()
    }
}