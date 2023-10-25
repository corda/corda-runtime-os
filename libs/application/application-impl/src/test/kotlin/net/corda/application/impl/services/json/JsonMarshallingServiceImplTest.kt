package net.corda.application.impl.services.json

import net.corda.v5.application.marshalling.json.JsonDeserializer
import net.corda.v5.application.marshalling.json.JsonNodeReader
import net.corda.v5.application.marshalling.json.JsonSerializer
import net.corda.v5.application.marshalling.json.JsonWriter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.reflect.ParameterizedType

class JsonMarshallingServiceImplTest {

    data class SimpleDto(
        var name: String? = null,
        var quantity: Int? = null
    )

    companion object {
        const val TEST_FIELD = "testfield"
        const val TEST_VALUE = "testvalue"
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

    @Suppress("EmptyClassBlock")
    class OtherDto

    class OtherSerializer : JsonSerializer<OtherDto> {
        override fun serialize(item: OtherDto, jsonWriter: JsonWriter) {}
    }

    class OtherSimpleDtoSerializer : JsonSerializer<SimpleDto> {
        override fun serialize(item: SimpleDto, jsonWriter: JsonWriter) {}
    }

    class OtherSimpleDtoDeserializer : JsonDeserializer<SimpleDto> {
        override fun deserialize(jsonRoot: JsonNodeReader): SimpleDto {
            return SimpleDto()
        }
    }

    class OtherDeserializer : JsonDeserializer<OtherDto> {
        override fun deserialize(jsonRoot: JsonNodeReader): OtherDto {
            return OtherDto()
        }
    }

    private fun instanceOf(instanceClass: String): Any {
        val serializerClazz = Class.forName(instanceClass)
        return serializerClazz.getConstructor().newInstance()
    }

    /**
     * This simulates how the serializing type is extracted from serializers/deserializers at runtime
     */
    private inline fun <reified T : Any> extractSerializingType(jsonSerializer: T): Class<*> {
        val types = jsonSerializer::class.java.genericInterfaces
            .filterIsInstance<ParameterizedType>()
            .filter { it.rawType === T::class.java }
            .flatMap { it.actualTypeArguments.asList() }
        if (types.size != 1) {
            throw IllegalStateException("Unable to determine serialized type from JsonSerializer")
        }
        return Class.forName(types.first().typeName)
    }

    @Test
    fun `Can serialize object to json string`() {
        val dto = SimpleDto(
            name = "n1",
            quantity = 1
        )

        val json = JsonMarshallingServiceImpl().format(dto)
        assertThat(json).isEqualTo("""
            {
              "name": "n1",
              "quantity": 1
            }
        """.filter { !it.isWhitespace() })
    }

    @Test
    fun `Can deserialize object from json string`() {
        val dto = JsonMarshallingServiceImpl().parse("""
            {
              "name": "n1",
              "quantity": 1
            }
        """.filter { !it.isWhitespace() },SimpleDto::class.java)
        assertThat(dto.name).isEqualTo("n1")
        assertThat(dto.quantity).isEqualTo(1)
    }

    @Test
    fun `Can deserialize list from json string`() {
        val dtoList = JsonMarshallingServiceImpl().parseList("""
            [
              {
                "name": "n1",
                "quantity": 1
              },
              {
                "name": "n2",
                "quantity": 2
              }
            ]
        """.filter { !it.isWhitespace() },
            SimpleDto::class.java
        )
        assertThat(dtoList).hasSize(2)
        assertThat(dtoList[0].name).isEqualTo("n1")
        assertThat(dtoList[0].quantity).isEqualTo(1)
        assertThat(dtoList[1].name).isEqualTo("n2")
        assertThat(dtoList[1].quantity).isEqualTo(2)
        assertThrows<UnsupportedOperationException> { (dtoList as MutableList<*>).clear() }
    }

    @Test
    fun `can deserialize map from json string`() {
        val dtoMap = JsonMarshallingServiceImpl().parseMap("""
            {
              "100": {
                "name": "n1",
                "quantity": 1
              },
              "200": {
                "name": "n2",
                "quantity": 2
              },
              "300": {
                "name": "n3",
                "quantity": 3
              }
            }
        """.trimIndent(),
            Int::class.java,
            SimpleDto::class.java
        )
        assertThat(dtoMap).hasSize(3)
        assertThat(dtoMap.keys).containsExactly(100, 200, 300)
        assertThat(dtoMap[100]).isEqualTo(SimpleDto("n1", 1))
        assertThat(dtoMap[200]).isEqualTo(SimpleDto("n2", 2))
        assertThat(dtoMap[300]).isEqualTo(SimpleDto("n3", 3))
        assertThrows<UnsupportedOperationException> { (dtoMap as MutableMap<*,*>).clear() }
    }

    @Test
    fun `Serialize with custom serializer`() {
        // In the real world the serializer is instantiated at run time, so we simulate that here in order to test we
        // never rely on compile time type information passed to generic methods or classes
        val instance = instanceOf(
            "net.corda.application.impl.services.json.JsonMarshallingServiceImplTest\$SimpleSerializer"
        )

        val jms = JsonMarshallingServiceImpl()
        jms.setSerializer(instance as JsonSerializer<*>, extractSerializingType(instance))

        val dto = SimpleDto(
            name = "n1",
            quantity = 1
        )
        assertThat(jms.format(dto)).isEqualTo("""
            {
              "$TEST_FIELD": "$TEST_VALUE"
            }
        """.filter { !it.isWhitespace() })
    }

    @Test
    fun `Deserialize with custom serializer`() {
        // In the real world the deserializer is instantiated at run time, so we simulate that here in order to test we
        // never rely on compile time type information passed to generic methods or classes
        val instance = instanceOf(
            "net.corda.application.impl.services.json.JsonMarshallingServiceImplTest\$SimpleDeserializer"
        )

        val jms = JsonMarshallingServiceImpl()
        jms.setDeserializer(instance as JsonDeserializer<*>, extractSerializingType(instance))

        val dto = jms.parse("""
            {
              "$TEST_FIELD": "$TEST_VALUE"
            }
        """.filter { !it.isWhitespace() }, SimpleDto::class.java)

        assertThat(dto.name).isEqualTo(TEST_VALUE)
        assertThat(dto.quantity).isEqualTo(42)
    }

    @Test
    fun `Duplicate serializers are rejected`() {
        val jms = JsonMarshallingServiceImpl()
        assertTrue(jms.setSerializer(SimpleSerializer(), SimpleDto::class.java))
        assertTrue(jms.setSerializer(OtherSerializer(), OtherDto::class.java))
        assertFalse(jms.setSerializer(SimpleSerializer(), SimpleDto::class.java)) // exact duplicate
        assertFalse(jms.setSerializer(OtherSimpleDtoSerializer(), SimpleDto::class.java)) // different serializer, same type
    }

    @Test
    fun `Duplicate deserializers are rejected`() {
        val jms = JsonMarshallingServiceImpl()
        assertTrue(jms.setDeserializer(SimpleDeserializer(), SimpleDto::class.java))
        assertTrue(jms.setDeserializer(OtherDeserializer(), OtherDto::class.java))
        assertFalse(jms.setDeserializer(SimpleDeserializer(), SimpleDto::class.java)) // exact duplicate
        assertFalse(jms.setDeserializer(OtherSimpleDtoDeserializer(), SimpleDto::class.java)) // different deserializer, same type
    }

    @Test
    fun `Nested field with custom serializer and deserializer picks them up correctly`() {
        val jms = JsonMarshallingServiceImpl()

        // ComplexDto has no explicit serializer or deserializer but contains a field which does. The
        // JsonMarshallingService should automatically recognise the custom field serialization even though the outer
        // class is using default serialization.
        jms.setDeserializer(NestedFieldDeserializer(), NestedField::class.java)
        jms.setSerializer(NestedFieldSerializer(), NestedField::class.java)

        val dtoToSerialize = ComplexDto(name = "name-test", field = NestedField(contents = "anything"))
        val serializedJson = jms.format(dtoToSerialize)

        assertEquals("""
            {
              "name": "name-test",
              "field": "anything-written-nested-field"
            }
        """.filter { !it.isWhitespace() }, serializedJson)

        val dto = jms.parse(serializedJson, ComplexDto::class.java)
        assertEquals("name-test", dto.name)
        assertEquals("anything-written-nested-field-deserialized", dto.field?.contents)
    }
}
