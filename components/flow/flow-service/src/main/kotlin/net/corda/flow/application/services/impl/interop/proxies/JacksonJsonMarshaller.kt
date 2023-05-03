package net.corda.flow.application.services.impl.interop.proxies

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.v5.application.marshalling.JsonMarshallingService

class JacksonJsonMarshaller(private val objectMapper: ObjectMapper) : JsonMarshaller {
    override fun serialize(value: Any): String = objectMapper.writeValueAsString(value)

    override fun <T : Any> deserialize(value: String, type: Class<T>): T = objectMapper.readValue(value, type)

}

class JacksonJsonMarshallerAdaptor(private val jsonMarshallingService: JsonMarshallingService) : JsonMarshaller {
    override fun serialize(value: Any): String = jsonMarshallingService.format(value)

    override fun <T : Any> deserialize(value: String, type: Class<T>): T = jsonMarshallingService.parse(value, type)

}