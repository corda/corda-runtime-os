package net.corda.simulator.runtime.serialization

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.corda.common.json.serializers.standardTypesModule
import net.corda.simulator.RequestData
import net.corda.simulator.runtime.RPCRequestDataWrapper
import net.corda.simulator.runtime.utils.publicKeyModule
import net.corda.v5.application.marshalling.JsonMarshallingService

/**
 * A simple JsonMarshallingService, without the caching that Corda uses.
 */
class SimpleJsonMarshallingService : JsonMarshallingService{

    private val objectMapper = jacksonObjectMapper()

    init {
        val module = SimpleModule()
        module.addDeserializer(RequestData::class.java, RequestDataSerializer())
        objectMapper.registerModule(module)
        objectMapper.registerModule(standardTypesModule())
        objectMapper.registerModule(publicKeyModule())
    }

    override fun format(data: Any): String {
        return objectMapper.writeValueAsString(data)
    }

    override fun <T> parse(input: String, clazz: Class<T>): T {
        return objectMapper.readValue(input, clazz)
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
