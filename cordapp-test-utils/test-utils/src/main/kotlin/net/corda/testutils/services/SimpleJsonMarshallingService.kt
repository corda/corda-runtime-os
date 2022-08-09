package net.corda.testutils.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.corda.v5.application.marshalling.JsonMarshallingService

class SimpleJsonMarshallingService : JsonMarshallingService{

    private val objectMapper = jacksonObjectMapper()

    override fun format(data: Any): String {
        return objectMapper.writeValueAsString(data)
    }

    override fun <T> parse(input: String, clazz: Class<T>): T {
        return objectMapper.readValue<T>(input, clazz)
    }

    override fun <T> parseList(input: String, clazz: Class<T>): List<T> {
        return objectMapper.readValue(input, objectMapper.typeFactory.constructCollectionType(List::class.java, clazz))
    }

}
