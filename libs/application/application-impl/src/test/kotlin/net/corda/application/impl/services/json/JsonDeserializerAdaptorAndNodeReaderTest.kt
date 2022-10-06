package net.corda.application.impl.services.json

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import net.corda.v5.application.marshalling.json.JsonDeserializer
import net.corda.v5.application.marshalling.json.JsonNodeReader
import net.corda.v5.application.marshalling.json.parse
import org.junit.jupiter.api.Test

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
                "field1": "v-contents1",
                "field2": "v-contents2"
              },
              "object3": {
                "inside-object-3": "inside-object3-value",
                "inside-object-3b": "inside-object3-valueb"
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

    private class TestDeserializer : JsonDeserializer<TestClass> {
        override fun deserialize(jsonRoot: JsonNodeReader): TestClass {
            jsonRoot.fields()!!.forEach {
                println("${it.key}  ${it.value.asText()}")

                if (it.key == "array5") {
                    println("---------");
                    it.value.asArray()?.forEach {
                        println("array value: ${it.asText()}")
                    }
                    println("---------");
                }

                if (it.key == "object2") {
                    println("---------");
                    val ds = it.value.parse<DefaultDeserializable>()
                    ds.hashCode()
                    println("---------");
                }

                if (it.key == "object3") {
                    println("---------");
                    it.value.fields()?.forEach {
                        println("${it.key}  ${it.value.asText()}")
                    }
                    println("---------");
                }
            }

            return TestClass()
        }
    }

    @Test
    fun `validate deserializer adaptor and JsonNodeReaderAdaptor`() {
        val mapper = ObjectMapper()
        val module = SimpleModule()
        module.addDeserializer(TestClass::class.java, jsonDeserializerAdaptorOf(TestDeserializer()))
        mapper.registerModule(module)
        mapper.readValue(JSON_TO_PARSE, TestClass::class.java)
    }
}
