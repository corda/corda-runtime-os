package net.corda.simulator.runtime.serialization

import net.corda.simulator.factories.JsonMarshallingServiceFactory
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.marshalling.json.JsonDeserializer
import net.corda.v5.application.marshalling.json.JsonSerializer

/**
 * @see [JsonMarshallingServiceFactory].
 */
class SimpleJsonMarshallingServiceFactory : JsonMarshallingServiceFactory {
    override fun create(
        customJsonSerializers: Map<JsonSerializer<*>, Class<*>>,
        customJsonDeserializers: Map<JsonDeserializer<*>, Class<*>>
    ): JsonMarshallingService {
        return SimpleJsonMarshallingService(
            customJsonSerializers, customJsonDeserializers
        )
    }
}