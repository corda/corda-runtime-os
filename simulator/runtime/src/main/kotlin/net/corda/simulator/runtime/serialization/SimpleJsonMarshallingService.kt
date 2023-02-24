package net.corda.simulator.runtime.serialization

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.util.Collections.unmodifiableList
import java.util.Collections.unmodifiableMap
import net.corda.common.json.serializers.JsonDeserializerAdaptor
import net.corda.common.json.serializers.JsonSerializerAdaptor
import net.corda.common.json.serializers.standardTypesModule
import net.corda.simulator.RequestData
import net.corda.simulator.runtime.RPCRequestDataWrapper
import net.corda.simulator.runtime.utils.publicKeyModule
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.marshalling.json.JsonDeserializer
import net.corda.v5.application.marshalling.json.JsonSerializer
import net.corda.v5.base.util.uncheckedCast

/**
 * A simple JsonMarshallingService, without the caching that Corda uses.
 * @param customSerializer A map of custom JsonSerializer to its type
 * @param customDeserializer A map of custom JsonDeserializer to its type
 */
class SimpleJsonMarshallingService(
    customSerializer : Map<JsonSerializer<*>, Class<*>> = mapOf(),
    customDeserializer : Map<JsonDeserializer<*>, Class<*>> = mapOf()
) : JsonMarshallingService{

    private val objectMapper = jacksonObjectMapper()
    private val customSerializableClasses = mutableSetOf<Class<*>>()
    private val customDeserializableClasses = mutableSetOf<Class<*>>()

    init {
        val module = SimpleModule()
        module.addDeserializer(RequestData::class.java, RequestDataSerializer())
        objectMapper.registerModule(module)
        objectMapper.registerModule(standardTypesModule())
        objectMapper.registerModule(publicKeyModule())

        customSerializer.mapKeys { setSerializer(it.key, it.value) }
        customDeserializer.mapKeys { setDeserializer(it.key, it.value) }
    }

    override fun format(data: Any): String {
        return objectMapper.writeValueAsString(data)
    }

    override fun <T> parse(input: String, clazz: Class<T>): T {
        return objectMapper.readValue(input, clazz)
    }

    override fun <T> parseList(input: String, clazz: Class<T>): List<T> {
        return unmodifiableList(objectMapper.readValue(
            input, objectMapper.typeFactory.constructCollectionType(List::class.java, clazz)))
    }

    override fun <K, V> parseMap(input: String, keyClass: Class<K>, valueClass: Class<V>): Map<K, V> {
        return unmodifiableMap(objectMapper.readValue(
            input, objectMapper.typeFactory.constructMapType(LinkedHashMap::class.java, keyClass, valueClass)))
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

    private fun setSerializer(serializer: JsonSerializer<*>, type: Class<*>): Boolean {
        val jsonSerializerAdaptor = JsonSerializerAdaptor(serializer, type)
        if (customSerializableClasses.contains(jsonSerializerAdaptor.serializingType)) return false
        customSerializableClasses.add(jsonSerializerAdaptor.serializingType)

        val module = SimpleModule()
        module.addSerializer(jsonSerializerAdaptor.serializingType, jsonSerializerAdaptor)
        objectMapper.registerModule(module)

        return true
    }

    private fun setDeserializer(deserializer: JsonDeserializer<*>, type: Class<*>): Boolean {
        val jsonDeserializerAdaptor = JsonDeserializerAdaptor(deserializer, type)
        if (customDeserializableClasses.contains(jsonDeserializerAdaptor.deserializingType)) return false
        customDeserializableClasses.add(jsonDeserializerAdaptor.deserializingType)

        val module = SimpleModule()
        module.addDeserializer(uncheckedCast(jsonDeserializerAdaptor.deserializingType), jsonDeserializerAdaptor)
        objectMapper.registerModule(module)
        return true
    }

}
