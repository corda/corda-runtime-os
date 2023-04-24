package org.corda.weft.proxies

import net.corda.v5.application.marshalling.JsonMarshallingService
import org.corda.weft.api.JsonMarshaller

class JacksonJsonMarshaller(private val serializer: JsonMarshallingService) : JsonMarshaller {
    override fun serialize(value: Any): String = serializer.format(value)

    override fun <T : Any> deserialize(value: String, type: Class<T>): T = serializer.parse(value, type)

}