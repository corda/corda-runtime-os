package net.corda.common.json.serializers

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import net.corda.v5.application.marshalling.json.JsonDeserializer
import net.corda.v5.application.marshalling.json.JsonNodeReader
import net.corda.v5.application.marshalling.json.JsonNodeReaderType
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.BigInteger

class JsonDeserializerAdaptorAndNodeReaderTest {
    /**
     * We have no requirement to deserialize into actual properties of a class, that doesn't prove anything, so our test
     * class can be completely empty. The test deserializer will parse and validate the result as it goes directly.
     */
    @Suppress("EmptyClassBlock")
    private class TestClass {}

    private data class DefaultDeserializable(
        val field1: String = "",
        val field2: String = ""
    )

    private val JSON_TO_PARSE = """
            {
              "string1": "string-value1",
              "string2": "string-value2",
              "string3": "1.1",
              "number1": 1,
              "number2": 1.1,
              "number3": 32768,
              "object1": {
                "field1": "v-contents1",
                "field2": "v-contents2"
              },
              "boolean1": true,
              "boolean2": false,
              "array1": [
                "inside-array1a",
                "inside-array1b"
              ],
              "binary1": "YmluYXJ5MS1kYXRh",
              "null1": null
            }
        """.filter { !it.isWhitespace() }

    private class TestDeserializer : JsonDeserializer<TestClass> {
        override fun deserialize(jsonRoot: JsonNodeReader): TestClass {
            /*
             * The underlying implementation is provided by Jackson, here we do not try to test every input and output,
             * particularly where Jackson defers to Java coercion rules for example.
             * Instead, we attempt to prove each method in our interface forwards to the correct Jackson method and that
             * our understanding of the underlying Jackson method where it is represented in our own documentation is
             * correct.
             */

            // Type/root node test
            assertEquals(jsonRoot.getType(), JsonNodeReaderType.OBJECT)
            assertTrue(jsonRoot.isObject())

            // Fields
            jsonRoot.fields()!!.let {
                it.next().let {
                    assertEquals("string1", it.key)
                    assertEquals("string-value1", it.value.asText())
                }
                it.next().let {
                    assertEquals("string2", it.key)
                    assertEquals("string-value2", it.value.asText())
                }
            }
            assertNull(jsonRoot.getField("non-existent"))

            // Object
            assertEquals(2, jsonRoot.getField("object1")!!.fields()!!.asSequence().count())
            jsonRoot.getField("object1")!!.fields()!!.let {
                it.next().let {
                    assertEquals("field1", it.key)
                    assertEquals("v-contents1", it.value.asText())
                }
                it.next().let {
                    assertEquals("field2", it.key)
                    assertEquals("v-contents2", it.value.asText())
                }
            }
            val expectedParsedObject = DefaultDeserializable("v-contents1", "v-contents2")
            assertEquals(expectedParsedObject, jsonRoot.getField("object1")!!.parse(DefaultDeserializable::class.java))

            // Arrays
            assertTrue(jsonRoot.hasField("array1"))
            assertTrue(jsonRoot.getField("array1")!!.isArray())
            assertFalse(jsonRoot.getField("string1")!!.isArray())
            assertFalse(jsonRoot.getField("object1")!!.isArray())
            val arrayIt1 = jsonRoot.getField("array1")!!.asArray()!!
            assertEquals(2, arrayIt1.asSequence().count())
            val arrayIt2 = jsonRoot.getField("array1")!!.asArray()!!
            assertEquals("inside-array1a", arrayIt2.next().asText())
            assertEquals("inside-array1b", arrayIt2.next().asText())

            // Booleans
            assertTrue(jsonRoot.getField("boolean1")!!.isBoolean())
            assertTrue(jsonRoot.getField("boolean1")!!.asBoolean())
            assertTrue(jsonRoot.getField("boolean2")!!.isBoolean())
            assertFalse(jsonRoot.getField("string1")!!.isBoolean())
            assertFalse(jsonRoot.getField("number1")!!.isBoolean())
            assertFalse(jsonRoot.getField("boolean2")!!.asBoolean())
            assertTrue(jsonRoot.getField("boolean1")!!.asBoolean(false))
            assertTrue(jsonRoot.getField("string1")!!.asBoolean(true))

            // Number
            assertTrue(jsonRoot.getField("number1")!!.isNumber())
            assertTrue(jsonRoot.getField("number2")!!.isNumber())
            assertFalse(jsonRoot.getField("string3")!!.isNumber())
            assertEquals(1, jsonRoot.getField("number1")!!.numberValue())
            assertEquals(1.1, jsonRoot.getField("number2")!!.numberValue())
            assertFalse(jsonRoot.getField("number1")!!.isFloatingPointNumber())
            assertTrue(jsonRoot.getField("number2")!!.isFloatingPointNumber())

            // Double
            assertFalse(jsonRoot.getField("number1")!!.isDouble())
            assertTrue(jsonRoot.getField("number2")!!.isDouble())
            assertFalse(jsonRoot.getField("string3")!!.isDouble())
            assertEquals(1.0, jsonRoot.getField("number1")!!.doubleValue())
            assertEquals(1.1, jsonRoot.getField("number2")!!.doubleValue())
            assertEquals(0.0, jsonRoot.getField("string1")!!.doubleValue())
            assertEquals(1.0, jsonRoot.getField("number1")!!.asDouble())
            assertEquals(1.1, jsonRoot.getField("number2")!!.asDouble())
            assertEquals(1.1, jsonRoot.getField("string3")!!.asDouble())
            assertEquals(0.0, jsonRoot.getField("string1")!!.asDouble())
            assertEquals(2.2, jsonRoot.getField("string1")!!.asDouble(2.2))
            assertEquals(1.0, jsonRoot.getField("boolean1")!!.asDouble())
            assertEquals(0.0, jsonRoot.getField("boolean2")!!.asDouble())

            // Float
            assertEquals(1.0f, jsonRoot.getField("number1")!!.floatValue())
            assertEquals(1.1f, jsonRoot.getField("number2")!!.floatValue())
            assertEquals(0.0f, jsonRoot.getField("string1")!!.floatValue())

            // Int
            assertTrue(jsonRoot.getField("number1")!!.isInt())
            assertFalse(jsonRoot.getField("number2")!!.isInt())
            assertFalse(jsonRoot.getField("string3")!!.isInt())
            assertTrue(jsonRoot.getField("number1")!!.canConvertToInt())
            assertTrue(jsonRoot.getField("number2")!!.canConvertToInt())
            assertFalse(jsonRoot.getField("string3")!!.canConvertToInt())
            assertEquals(1, jsonRoot.getField("number1")!!.asInt())
            assertEquals(1, jsonRoot.getField("number2")!!.asInt())
            assertEquals(1, jsonRoot.getField("string3")!!.asInt())
            assertEquals(0, jsonRoot.getField("string1")!!.asInt())
            assertEquals(2, jsonRoot.getField("string1")!!.asInt(2))
            assertEquals(1, jsonRoot.getField("boolean1")!!.asInt())
            assertEquals(0, jsonRoot.getField("boolean2")!!.asInt())

            // Long
            assertTrue(jsonRoot.getField("number1")!!.canConvertToLong())
            assertTrue(jsonRoot.getField("number2")!!.canConvertToLong())
            assertFalse(jsonRoot.getField("string3")!!.canConvertToLong())
            assertEquals(1L, jsonRoot.getField("number1")!!.asLong())
            assertEquals(1L, jsonRoot.getField("number2")!!.asLong())
            assertEquals(32768L, jsonRoot.getField("number3")!!.asLong())
            assertEquals(1L, jsonRoot.getField("string3")!!.asLong())
            assertEquals(0L, jsonRoot.getField("string1")!!.asLong())
            assertEquals(2L, jsonRoot.getField("string1")!!.asLong(2L))
            assertEquals(1L, jsonRoot.getField("boolean1")!!.asLong())
            assertEquals(0L, jsonRoot.getField("boolean2")!!.asLong())

            // Short
            assertEquals(1, jsonRoot.getField("number1")!!.shortValue())
            assertEquals(1, jsonRoot.getField("number2")!!.shortValue())
            // Not so bothered about how number3 is coerced into a Short as long as it is
            assertNotEquals(0, jsonRoot.getField("number3")!!.shortValue())
            assertEquals(0, jsonRoot.getField("string1")!!.shortValue())
            assertEquals(0, jsonRoot.getField("string3")!!.shortValue())

            // BigInteger
            assertEquals(BigInteger.valueOf(1L), jsonRoot.getField("number1")!!.bigIntegerValue())
            assertEquals(BigInteger.valueOf(1L), jsonRoot.getField("number2")!!.bigIntegerValue())
            assertEquals(BigInteger.valueOf(32768L), jsonRoot.getField("number3")!!.bigIntegerValue())
            assertEquals(BigInteger.valueOf(0L), jsonRoot.getField("string1")!!.bigIntegerValue())
            assertEquals(BigInteger.valueOf(0L), jsonRoot.getField("string3")!!.bigIntegerValue())

            // BigDecimal
            // Not really epsilon, a wild approximation, we only need to be as precise as 1dp in the tests, we are not
            // interested in testing precision at all
            val epsilon = 0.001
            assertEquals(BigDecimal(1).toDouble(), jsonRoot.getField("number1")!!.bigDecimalValue().toDouble(), epsilon)
            assertEquals(
                BigDecimal(1.1).toDouble(),
                jsonRoot.getField("number2")!!.bigDecimalValue().toDouble(),
                epsilon
            )
            assertEquals(
                BigDecimal(32768).toDouble(),
                jsonRoot.getField("number3")!!.bigDecimalValue().toDouble(),
                epsilon
            )
            assertEquals(BigDecimal(0).toDouble(), jsonRoot.getField("string1")!!.bigDecimalValue().toDouble(), epsilon)
            assertEquals(BigDecimal(0).toDouble(), jsonRoot.getField("string3")!!.bigDecimalValue().toDouble(), epsilon)

            // Text
            assertTrue(jsonRoot.getField("string1")!!.isText())
            assertFalse(jsonRoot.getField("number1")!!.isText())
            assertFalse(jsonRoot.getField("object1")!!.isText())
            assertFalse(jsonRoot.getField("array1")!!.isText())
            assertFalse(jsonRoot.getField("null1")!!.isText())
            assertEquals("string-value1", jsonRoot.getField("string1")!!.asText())
            assertEquals("1", jsonRoot.getField("number1")!!.asText())
            assertEquals("YmluYXJ5MS1kYXRh", jsonRoot.getField("binary1")!!.asText())
            assertEquals("", jsonRoot.getField("object1")!!.asText())
            assertEquals("", jsonRoot.getField("array1")!!.asText())
            assertEquals("null", jsonRoot.getField("null1")!!.asText())
            assertEquals("default", jsonRoot.getField("null1")!!.asText("default"))

            // Binary
            assertArrayEquals("binary1-data".toByteArray(), jsonRoot.getField("binary1")!!.binaryValue())
            assertNull(jsonRoot.getField("string1")!!.binaryValue())
            assertNull(jsonRoot.getField("number1")!!.binaryValue())

            // Null
            assertTrue(jsonRoot.getField("null1")!!.isNull())
            assertFalse(jsonRoot.getField("string1")!!.isNull())

            return TestClass()
        }
    }

    /**
     * We create the test serializer from a factory function because it more closely resembles the dynamic nature in
     * which they will be created at runtime in Corda. We need to make sure no compile time type information is required
     * to register a serializer.
     */
    private fun testDeserializerFactory(): Pair<JsonDeserializer<*>, Class<*>> {
        return Pair(TestDeserializer(), TestClass::class.java)
    }

    @Test
    fun `validate deserializer adaptor and JsonNodeReaderAdaptor`() {
        val mapper = ObjectMapper()
        val module = SimpleModule()
        val (deserializer, type) = testDeserializerFactory()
        // Note that the deserializing type is passed in as a Class<Any> not a Class<*>. This is required because the
        // Jackson api type parameter of the Class is restricted to types or subtypes of the type the StdDeserializer
        // understands, and in our case that is an Any. Because of erasure all this is irrelevant internally, Jackson
        // cannot track types except those that the clazz represents, which is always the specific type we want
        // deserializing.
        val jsonDeserializerAdaptor = JsonDeserializerAdaptor(deserializer, type)
        @Suppress("unchecked_cast")
        module.addDeserializer(jsonDeserializerAdaptor.deserializingType as Class<Any>, jsonDeserializerAdaptor)
        mapper.registerModule(module)
        mapper.readValue(JSON_TO_PARSE, TestClass::class.java)
    }
}
