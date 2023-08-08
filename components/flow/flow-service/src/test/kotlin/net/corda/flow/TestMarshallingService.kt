package net.corda.flow

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.corda.v5.application.marshalling.JsonMarshallingService

internal class TestMarshallingService : JsonMarshallingService {
    private val objectMapper = jacksonObjectMapper()

    override fun format(data: Any): String {
        return objectMapper.writeValueAsString(data)
    }

    override fun <T> parse(input: String, clazz: Class<T>): T {
        return objectMapper.readValue(input, clazz)
    }

    override fun <T> parseList(input: String, clazz: Class<T>): List<T> {
        return objectMapper.readValue(
            input,
            objectMapper.typeFactory.constructCollectionType(List::class.java, clazz)
        )
    }

    override fun <K, V> parseMap(input: String, keyClass: Class<K>, valueClass: Class<V>): Map<K, V> {
        return objectMapper.readValue(
            input,
            objectMapper.typeFactory.constructMapType(LinkedHashMap::class.java, keyClass, valueClass)
        )
    }
}