package net.corda.simulator.runtime.serialization

import net.corda.simulator.factories.JsonMarshallingServiceFactory
import net.corda.v5.application.marshalling.JsonMarshallingService

/**
 * @see [JsonMarshallingServiceFactory].
 */
class SimpleJsonMarshallingServiceFactory : JsonMarshallingServiceFactory {
    override fun create(): JsonMarshallingService {
        return SimpleJsonMarshallingService()
    }
}