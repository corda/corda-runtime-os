package net.corda.cordapptestutils.internal.tools

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.corda.cordapptestutils.RequestData
import net.corda.cordapptestutils.internal.RPCRequestDataWrapper
import net.corda.v5.application.marshalling.JsonMarshallingService

class SimpleJsonMarshallingService : JsonMarshallingService{

    private val objectMapper = jacksonObjectMapper()

    init {
        val module = SimpleModule()
        module.addDeserializer(RequestData::class.java, RequestDataSerializer())
        objectMapper.registerModule(module)
    }

    override fun format(data: Any): String {
        return objectMapper.writeValueAsString(data)
    }

    override fun <T> parse(input: String, clazz: Class<T>): T {
        return objectMapper.readValue<T>(input, clazz)
    }

    override fun <T> parseList(input: String, clazz: Class<T>): List<T> {
        return objectMapper.readValue(input, objectMapper.typeFactory.constructCollectionType(List::class.java, clazz))
    }

    private class RequestDataSerializer : StdDeserializer<RequestData>(RequestData::class.java) {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): RequestData {
            val node: JsonNode = p.codec.readTree(p)
            val clientRequestId = node.get("clientRequestId").asText()
            val flowClassName = node.get("flowClassName").asText()
            val requestBody = node.get("requestBody").asText()

            return RPCRequestDataWrapper(clientRequestId, flowClassName, requestBody)
        }

    }

}
