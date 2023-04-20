package net.corda.flow.application.services.impl.interop.proxies

import com.fasterxml.jackson.databind.ObjectMapper

class JacksonJsonMarshaller(private val objectMapper: ObjectMapper) : JsonMarshaller {
    override fun serialize(value: Any): String = objectMapper.writeValueAsString(value)

    override fun <T : Any> deserialize(value: String, type: Class<T>): T = objectMapper.readValue(value, type)

}