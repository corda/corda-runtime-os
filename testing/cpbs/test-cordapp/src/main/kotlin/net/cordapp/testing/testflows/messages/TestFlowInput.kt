package net.cordapp.testing.testflows.messages

import net.corda.v5.application.marshalling.json.JsonDeserializer
import net.corda.v5.application.marshalling.json.JsonNodeReader
import net.corda.v5.application.marshalling.json.JsonSerializer
import net.corda.v5.application.marshalling.json.JsonWriter

class TestFlowInput {
    var inputValue: String? = null
    var memberInfoLookup: String? = null
    var throwException: Boolean = false
}

@Suppress("Unused")
class TestSerializer : JsonSerializer<TestFlowInput> {
    override fun serialize(item: TestFlowInput, jsonWriter: JsonWriter) {
        jsonWriter.writeStartObject()
        jsonWriter.writeFieldName(TestFlowInput::inputValue::class.java.name)
        jsonWriter.writeString(item.inputValue ?: "")
        jsonWriter.writeFieldName(TestFlowInput::memberInfoLookup::class.java.name)
        jsonWriter.writeString(item.memberInfoLookup ?: "")
        jsonWriter.writeFieldName(TestFlowInput::throwException::class.java.name)
        jsonWriter.writeBoolean(item.throwException)
        jsonWriter.writeEndObject()
    }
}

@Suppress("Unused")
class TestDeserializer : JsonDeserializer<TestFlowInput> {
    override fun deserialize(jsonRoot: JsonNodeReader): TestFlowInput {
        return TestFlowInput().apply {
            inputValue = jsonRoot.asText()
            memberInfoLookup = jsonRoot.asText()
            throwException = jsonRoot.asBoolean()
        }
    }
}


