package net.corda.rest.json.serialization

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.TreeNode
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.node.ValueNode
import net.corda.rest.JsonObject

object JsonObjectSerializer : JsonSerializer<JsonObject>() {

    private val mapper = jacksonObjectMapper()
    override fun serialize(obj: JsonObject, generator: JsonGenerator, provider: SerializerProvider) {
        // Check if `escapedJson` is a valid JSON content
        val actualJsonNode: JsonNode? = try {
            mapper.readTree(obj.escapedJson)
        } catch (ex: Exception) {
            // Assuming not a valid Json then
            null
        }

        if (actualJsonNode != null) {
            generator.writeTree(actualJsonNode)
        } else {
            generator.writeString(obj.escapedJson)
        }
    }
}

object JsonObjectDeserializer : JsonDeserializer<JsonObject>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): JsonObject {
        val jsonValue = p.readValueAsTree<TreeNode>().let {
            if (it.isValueNode) {
                (it as ValueNode).textValue()
            } else {
                it.toString()
            }
        }
        return JsonObjectAsString(jsonValue)
    }
}
