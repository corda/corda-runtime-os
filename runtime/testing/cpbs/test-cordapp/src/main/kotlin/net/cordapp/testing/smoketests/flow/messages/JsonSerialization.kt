package net.cordapp.testing.smoketests.flow.messages

import net.corda.v5.application.marshalling.json.JsonDeserializer
import net.corda.v5.application.marshalling.json.JsonNodeReader
import net.corda.v5.application.marshalling.json.JsonSerializer
import net.corda.v5.application.marshalling.json.JsonWriter
import net.corda.v5.base.types.MemberX500Name

data class JsonSerializationFlowOutput (
    val firstTest: JsonSerializationOutput? = null,
    val secondTest: MemberX500Name? = null
)

data class JsonSerializationInput (
    var content: String? = null
)

data class JsonSerializationOutput (
    var content: String? = null
)

@Suppress("Unused")
class TestJsonSerializer : JsonSerializer<JsonSerializationInput> {
    override fun serialize(item: JsonSerializationInput, jsonWriter: JsonWriter) {
        jsonWriter.writeStartObject()
        jsonWriter.writeStringField("field1", item.content!!)
        jsonWriter.writeStringField("field2", item.content!!)
        jsonWriter.writeEndObject()
    }
}

@Suppress("Unused")
class TestJsonSerializer2 : JsonSerializer<JsonSerializationOutput> {
    override fun serialize(item: JsonSerializationOutput, jsonWriter: JsonWriter) {
        jsonWriter.writeStartObject()
        jsonWriter.writeStringField("serialized-implicitly", item.content!!)
        jsonWriter.writeEndObject()
    }
}

@Suppress("Unused")
class TestJsonDeserializer : JsonDeserializer<JsonSerializationOutput> {
    override fun deserialize(jsonRoot: JsonNodeReader): JsonSerializationOutput {
        val f1 = jsonRoot.getField("field1")!!.asText()
        val f2 = jsonRoot.getField("field2")!!.asText()
        return JsonSerializationOutput("combined-" + f1 + f2)
    }
}
