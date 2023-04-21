package net.corda.simulator.runtime.serialization

import net.corda.simulator.RequestData
import net.corda.simulator.runtime.testflows.HelloFlow
import net.corda.simulator.runtime.testflows.InputMessage
import net.corda.v5.application.marshalling.json.JsonDeserializer
import net.corda.v5.application.marshalling.json.JsonNodeReader
import net.corda.v5.application.marshalling.json.JsonSerializer
import net.corda.v5.application.marshalling.json.JsonWriter
import org.assertj.core.api.Assertions
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.Is.`is`
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SimpleJsonMarsallingServiceTest {

    @Test
    fun `should be able to create an object and list of objects from valid Json data`() {
        val input = """
            {
                "name" : "Clint",
                "age" : 42,
                "children" : [{"name" : "Lila", "age" : 14}, {"name" : "Cooper"}, {"name" : "Nathaniel"}]
            }
        """.trimIndent()
        val record = SimpleJsonMarshallingService().parse(input, Record::class.java)
        assertThat(record, `is`(
            Record("Clint", 42, listOf(
                Record("Lila", 14),
                Record("Cooper"),
                Record("Nathaniel")
            ))
        ))
    }

    @Test
    fun `should be able to serialize and deserialize RequestData`() {
        val requestId = "r1"
        val flowClass = HelloFlow::class.java
        val requestBody = InputMessage("IRunCordapps")
        val requestData : RequestData = net.corda.simulator.runtime.RPCRequestDataWrapperFactory()
            .create(requestId, flowClass, requestBody)

        val service = SimpleJsonMarshallingService()

        val jsonified = service.format(requestData)
        val result = service.parse(jsonified, RequestData::class.java)

        assertThat(result, `is`(requestData))
    }

    @Test
    fun `should serialize with custom serializer`() {

        val jms = SimpleJsonMarshallingService(
            mapOf(SimpleSerializer() to SimpleDto::class.java)
        )

        val dto = SimpleDto(
            name = "n1",
            quantity = 1
        )
        Assertions.assertThat(jms.format(dto)).isEqualTo("""
            {
              "$TEST_FIELD": "$TEST_VALUE"
            }
        """.filter { !it.isWhitespace() })
    }

    @Test
    fun `should deserialize with custom deserializer`() {

        val jms = SimpleJsonMarshallingService(
            customDeserializer = mapOf(SimpleDeserializer() to SimpleDto::class.java)
        )

        val dto = jms.parse("""
            {
              "$TEST_FIELD": "$TEST_VALUE"
            }
        """.filter { !it.isWhitespace() }, SimpleDto::class.java)

        Assertions.assertThat(dto.name).isEqualTo(TEST_VALUE)
        Assertions.assertThat(dto.quantity).isEqualTo(42)
    }

    @Test
    fun `should pick up Nested field with custom serializer and deserializer correctly`() {
        // ComplexDto has no explicit serializer or deserializer but contains a field which does. The
        // JsonMarshallingService should automatically recognise the custom field serialization even though the outer
        // class is using default serialization.
        val jms = SimpleJsonMarshallingService(
            mapOf(NestedFieldSerializer() to NestedField::class.java),
            mapOf(NestedFieldDeserializer() to NestedField::class.java)
        )

        val dtoToSerialize = ComplexDto(name = "name-test", field = NestedField(contents = "anything"))
        val serializedJson = jms.format(dtoToSerialize)

        assertEquals("""
            {
              "name": "name-test",
              "field": "anything-written-nested-field"
            }
        """.filter { !it.isWhitespace() }, serializedJson
        )

        val dto = jms.parse(serializedJson, ComplexDto::class.java)
        assertEquals("name-test", dto.name)
        assertEquals("anything-written-nested-field-deserialized", dto.field?.contents)
    }

    data class Record(val name : String, val age : Int? = null, val children : List<Record> = listOf())

    data class SimpleDto(
        var name: String? = null,
        var quantity: Int? = null
    )

    companion object {
        val TEST_FIELD = "testfield"
        val TEST_VALUE = "testvalue"
    }

    class SimpleSerializer : JsonSerializer<SimpleDto> {
        override fun serialize(item: SimpleDto, jsonWriter: JsonWriter) {
            jsonWriter.writeStartObject()
            jsonWriter.writeStringField(TEST_FIELD, TEST_VALUE)
            jsonWriter.writeEndObject()
        }
    }

    class SimpleDeserializer : JsonDeserializer<SimpleDto> {
        override fun deserialize(jsonRoot: JsonNodeReader): SimpleDto {
            val value = jsonRoot.getField(TEST_FIELD)?.asText()
            return SimpleDto(name = value, quantity = 42)
        }
    }

    data class NestedField(
        var contents: String? = null
    )

    data class ComplexDto(
        var name: String? = null,
        var field: NestedField? = null
    )

    class NestedFieldSerializer : JsonSerializer<NestedField> {
        override fun serialize(item: NestedField, jsonWriter: JsonWriter) {
            jsonWriter.writeString(item.contents + "-written-nested-field")
        }
    }

    class NestedFieldDeserializer : JsonDeserializer<NestedField> {
        /**
         * jsonRoot in this case is treated directly as a value not an object type
         */
        override fun deserialize(jsonRoot: JsonNodeReader): NestedField =
            NestedField(contents = jsonRoot.asText() + "-deserialized")
    }

}