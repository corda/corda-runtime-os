package net.corda.simulator.factories

import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.marshalling.json.JsonDeserializer
import net.corda.v5.application.marshalling.json.JsonSerializer
import net.corda.v5.base.annotations.DoNotImplement
import java.util.ServiceLoader

/**
 * Factory for building a [JsonMarshallingService].
 */
@DoNotImplement
interface JsonMarshallingServiceFactory {

    companion object {

        private fun delegate(
            customJsonSerializers: Map<JsonSerializer<*>, Class<*>>,
            customJsonDeserializers: Map<JsonDeserializer<*>, Class<*>>
        ): JsonMarshallingService{
            return ServiceLoader.load(JsonMarshallingServiceFactory::class.java).first().create(
                customJsonSerializers, customJsonDeserializers
            )
        }

        /**
         * Creates a standalone service, which can be used outside of Simulator for
         * parsing and formatting of arbitrary objects into JSON.
         *
         * @param customJsonDeserializers A map of custom JsonDeserializer to its type
         * @param customJsonSerializers A map of custom JsonSerializer to its type
         *
         * @return A [JsonMarshallingService].
         */
        fun create(
            customJsonSerializers: Map<JsonSerializer<*>, Class<*>> = emptyMap(),
            customJsonDeserializers: Map<JsonDeserializer<*>, Class<*>> = emptyMap()
        ) : JsonMarshallingService {
            return delegate(customJsonSerializers, customJsonDeserializers)
        }
    }

    /**
     * @param customJsonDeserializers A map of custom JsonDeserializer to its type
     * @param customJsonSerializers A map of custom JsonSerializer to its type
     * @return A [JsonMarshallingService].
     */
    fun create(
        customJsonSerializers: Map<JsonSerializer<*>, Class<*>> = emptyMap(),
        customJsonDeserializers: Map<JsonDeserializer<*>, Class<*>> = emptyMap()
    ): JsonMarshallingService
}