package net.cordapp.testing.testflows.messages

import net.corda.v5.application.marshalling.json.JsonDeserializer
import net.corda.v5.application.marshalling.json.JsonNodeReader
import net.corda.v5.application.marshalling.json.JsonSerializer
import net.corda.v5.application.marshalling.json.JsonWriter

data class TestFlowOutput(
    val inputValue: String,
    val virtualNodeX500Name: String,
    val foundMemberInfo: String
)

@Suppress("Unused")
class TestFlowOutputSerializer : JsonSerializer<TestFlowOutput> {
    override fun serialize(item: TestFlowOutput, jsonWriter: JsonWriter) {
        jsonWriter.writeStartObject()
        jsonWriter.writeFieldName(TestFlowOutput::inputValue::class.java.name)
        jsonWriter.writeString(item.inputValue)
        jsonWriter.writeFieldName(TestFlowOutput::virtualNodeX500Name::class.java.name)
        jsonWriter.writeString(item.virtualNodeX500Name)
        jsonWriter.writeFieldName(TestFlowOutput::foundMemberInfo::class.java.name)
        jsonWriter.writeString(item.foundMemberInfo)
        jsonWriter.writeEndObject()
    }
}

@Suppress("Unused")
class TestFlowOutputDeserializer : JsonDeserializer<TestFlowOutput> {
    override fun deserialize(jsonRoot: JsonNodeReader): TestFlowOutput {
        return TestFlowOutput(
            inputValue = jsonRoot.asText(),
            virtualNodeX500Name = jsonRoot.asText(),
            foundMemberInfo = jsonRoot.asText(),
        )
    }
}


