package net.corda.common.json.serializers

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import net.corda.v5.application.marshalling.json.JsonSerializedBase64Config
import net.corda.v5.application.marshalling.json.JsonSerializer
import net.corda.v5.application.marshalling.json.JsonWriter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.io.InputStream
import java.math.BigDecimal
import java.math.BigInteger

class JsonSerializerAdaptorAndWriterTest {
    /**
     * We have no requirement to serialize actual properties of a class, that doesn't prove anything, so our test class
     * can be completely empty. The serializer will generate mock data itself.
     */
    @Suppress("EmptyClassBlock")
    private class TestClass {}

    companion object {
        // Create a static instance of TestClass only so we can assert the reference is the same in the custom serializer
        private val testClassInstance = TestClass()

        private val EXPECTED_JSON = """
            {
              "string1": "string-value1",
              "string2": "string-value2",
              "string3": "string-value3",
              "number1": 1,
              "number2": 1,
              "number3": 1.1,
              "number4": 1.1,
              "number5": 1,
              "number6": 1,
              "number7": 1,
              "number8": 1,
              "number9": 1.1,
              "number10": 1.1,
              "number11": 1,
              "number12": 1,
              "object1": {
                "contents": "contents"
              },
              "object2": {
                "contents": "contents"
              },
              "object3": {
                "inside-object-3": "inside-object3-value"
              },
              "boolean1": true,
              "boolean2": false,
              "array1": [
                "inside-array1a",
                "inside-array1b"
              ],
              "array2": [
                "inside-array2a",
                "inside-array2b"
              ],
              "array3": [
                1,
                2
              ],
              "array4": [
                1,
                2
              ],
              "array5": [
                1.1,
                2.2
              ],
              "array6": [
                "A",
                "B"
              ],
              "binary1": "YmluYXJ5MS1kYXRh",
              "binary2": "QUFBQUFBQUFBQUFB",
              "binary3": "YmluYXJ5My1kYXRh",
              "binary4": "YmluYXJ5NC1kYXRh",
              "binary5": "YmluYXJ5NS1kYXRh",
              "null1": null,
              "null2": null,
              "raw1": 123456,
              "raw2": 123,
              "raw3": 1234,
              "raw4": 1234
            }
        """.filter { !it.isWhitespace() }
    }

    private data class DefaultSerializable(val contents: String = "contents")

    /**
     * A test serializer we use to check the custom serialization scheme is working. This calls every method in the
     * [JsonWriter] and therefore acts as a test of both the serializer adaptor and [JsonWriter].
     */
    private class TestSerializer : JsonSerializer<TestClass> {
        override fun serialize(item: TestClass, jsonWriter: JsonWriter) {
            assertSame(testClassInstance, item)
            jsonWriter.writeStartObject();
            jsonWriter.writeFieldName("string1")
            jsonWriter.writeString("XXstring-value1XX".toCharArray(), 2, 13)
            jsonWriter.writeFieldName("string2")
            jsonWriter.writeString("string-value2")
            jsonWriter.writeStringField("string3", "string-value3");

            jsonWriter.writeFieldName("number1")
            jsonWriter.writeNumber(BigDecimal(1))
            jsonWriter.writeFieldName("number2")
            jsonWriter.writeNumber(BigInteger.ONE)
            jsonWriter.writeFieldName("number3")
            jsonWriter.writeNumber(1.1f)
            jsonWriter.writeFieldName("number4")
            jsonWriter.writeNumber(1.1)
            jsonWriter.writeFieldName("number5")
            jsonWriter.writeNumber(1)
            jsonWriter.writeFieldName("number6")
            jsonWriter.writeNumber(1L)
            jsonWriter.writeFieldName("number7")
            jsonWriter.writeNumber(1.toShort())
            jsonWriter.writeNumberField("number8", BigDecimal(1))
            jsonWriter.writeNumberField("number9", 1.1)
            jsonWriter.writeNumberField("number10", 1.1f)
            jsonWriter.writeNumberField("number11", 1)
            jsonWriter.writeNumberField("number12", 1L)

            jsonWriter.writeFieldName("object1")
            jsonWriter.writeObject(DefaultSerializable())
            jsonWriter.writeObjectField("object2", DefaultSerializable())
            jsonWriter.writeObjectFieldStart("object3")
            jsonWriter.writeStringField("inside-object-3", "inside-object3-value")
            jsonWriter.writeEndObject();

            jsonWriter.writeFieldName("boolean1")
            jsonWriter.writeBoolean(true)
            jsonWriter.writeBooleanField("boolean2", false)

            jsonWriter.writeArrayFieldStart("array1")
            jsonWriter.writeString("inside-array1a")
            jsonWriter.writeString("inside-array1b")
            jsonWriter.writeEndArray()
            jsonWriter.writeFieldName("array2")
            jsonWriter.writeStartArray()
            jsonWriter.writeString("inside-array2a")
            jsonWriter.writeString("inside-array2b")
            jsonWriter.writeEndArray()
            jsonWriter.writeFieldName("array3")
            jsonWriter.writeArray(arrayOf(0, 0, 1, 2, 0, 0).toIntArray(), 2, 2)
            jsonWriter.writeFieldName("array4")
            jsonWriter.writeArray(arrayOf(0L, 0L, 1L, 2L, 0L, 0L).toLongArray(), 2, 2)
            jsonWriter.writeFieldName("array5")
            jsonWriter.writeArray(arrayOf(0.0, 0.0, 1.1, 2.2, 0.0, 0.0).toDoubleArray(), 2, 2)
            jsonWriter.writeFieldName("array6")
            jsonWriter.writeArray(arrayOf("X", "X", "A", "B", "X", "X"), 2, 2)

            jsonWriter.writeFieldName("binary1")
            jsonWriter.writeBinary(
                JsonSerializedBase64Config.MIME_NO_LINEFEEDS,
                "xxbinary1-dataxx".toByteArray(),
                2,
                12
            )
            jsonWriter.writeFieldName("binary2")
            jsonWriter.writeBinary(
                JsonSerializedBase64Config.MIME,
                object : InputStream() {
                    override fun read(): Int = 65
                },
                12
            )
            jsonWriter.writeFieldName("binary3")
            jsonWriter.writeBinary("binary3-data".toByteArray())
            jsonWriter.writeFieldName("binary4")
            jsonWriter.writeBinary("XXbinary4-dataXX".toByteArray(), 2, 12)
            jsonWriter.writeBinaryField("binary5", "binary5-data".toByteArray())

            jsonWriter.writeFieldName("null1")
            jsonWriter.writeNull()
            jsonWriter.writeNullField("null2")

            // Note that writeRaw methods do not change the context of the jsonWriter, so the field and value and the
            // opening separator must all be written too.
            jsonWriter.writeRaw(",\"raw1\"") // field name in raw
            jsonWriter.writeRaw(':')
            jsonWriter.writeRaw("XX123XX".toCharArray(), 2, 3)
            jsonWriter.writeRaw("XX456XX", 2, 3)

            jsonWriter.writeFieldName("raw2")
            jsonWriter.writeRawValue("XX123XX", 2, 3)
            jsonWriter.writeFieldName("raw3")
            jsonWriter.writeRawValue("1234")
            jsonWriter.writeFieldName("raw4")
            jsonWriter.writeRawValue("XX1234XX", 2, 4)

            jsonWriter.writeEndObject();
        }
    }

    /**
     * We create the test serializer from a factory function because it more closely resembles the dynamic nature in
     * which they will be created at runtime in Corda. We need to make sure no compile time type information is required
     * to register a serializer.
     */
    private fun testSerializerFactory(): Pair<JsonSerializer<*>, Class<*>> {
        return Pair(TestSerializer(), TestClass::class.java)
    }

    @Test
    fun `validate serializer adaptor and JsonWriter`() {
        val mapper = ObjectMapper()

        val module = SimpleModule()
        val (serializer, type) = testSerializerFactory()
        val jsonSerializerAdaptor = JsonSerializerAdaptor(serializer, type)
        module.addSerializer(jsonSerializerAdaptor.serializingType, jsonSerializerAdaptor)
        mapper.registerModule(module)

        val serialized: String = mapper.writeValueAsString(testClassInstance)
        assertEquals(EXPECTED_JSON, serialized)
    }
}
