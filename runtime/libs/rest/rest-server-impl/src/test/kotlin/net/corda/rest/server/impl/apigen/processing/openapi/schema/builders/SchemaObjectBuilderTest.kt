package net.corda.rest.server.impl.apigen.processing.openapi.schema.builders

import com.fasterxml.jackson.annotation.JsonIgnore
import net.corda.rest.server.impl.apigen.models.GenericParameterizedType
import net.corda.rest.server.impl.apigen.processing.openapi.schema.DefaultSchemaModelProvider
import net.corda.rest.server.impl.apigen.processing.openapi.schema.ParameterizedClass
import net.corda.rest.server.impl.apigen.processing.openapi.schema.SchemaModelContextHolder
import net.corda.rest.server.impl.apigen.processing.openapi.schema.model.DataType
import net.corda.rest.server.impl.apigen.processing.openapi.schema.model.SchemaCollectionModel
import net.corda.rest.server.impl.apigen.processing.openapi.schema.model.SchemaObjectModel
import net.corda.rest.server.impl.apigen.processing.openapi.schema.model.SchemaRefObjectModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class SchemaObjectBuilderTest {

    @Test
    fun `build withRecursiveObject succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val builder = SchemaObjectBuilder(provider, schemaModelContextHolder)

        val result = builder.build(RecursiveClass::class.java, emptyList())

        assertEquals(DataType.OBJECT, result.type)
        assertNull(result.format)
        with(result as SchemaObjectModel) {
            with(properties["recursion"] as SchemaRefObjectModel) {
                assertEquals(
                    "RecursiveClass",
                    this.ref
                )
            }
        }
    }

    @Test
    fun `build withDeepRecursiveObject succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val builder = SchemaObjectBuilder(provider, schemaModelContextHolder)

        val result = builder.build(DeepRecursiveClass::class.java, emptyList())

        assertEquals(DataType.OBJECT, result.type)
        assertNull(result.format)
        with(result as SchemaObjectModel) {
            with(properties["value"] as SchemaRefObjectModel) {
                assertEquals(
                    "NestedClass",
                    this.ref
                )
            }
        }
        with(schemaModelContextHolder.getSchema(ParameterizedClass(NestedClass::class.java))!!) {
            assertEquals("DeepRecursiveClass", (this.properties["recursion"] as SchemaRefObjectModel).ref)
        }
    }

    @Test
    fun `build withListWithRecursion succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val builder = SchemaObjectBuilder(provider, schemaModelContextHolder)

        val result = builder.build(RecursiveList::class.java, emptyList())

        assertEquals(DataType.OBJECT, result.type)
        assertNull(result.format)
        with(result as SchemaObjectModel) {
            with(properties["recursion"] as SchemaCollectionModel) {
                assertEquals(false, uniqueItems)
                with(items) {
                    assertEquals(
                        "RecursiveList",
                        (this as SchemaRefObjectModel).ref
                    )
                }
            }
        }
    }

    @Test
    fun `build withRecursionFromGenericField succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val builder = SchemaObjectBuilder(provider, schemaModelContextHolder)

        val result = builder.build(
            RecursiveGenericClass::class.java,
            listOf(
                GenericParameterizedType(
                    RecursiveGenericClass::class.java, listOf(
                        GenericParameterizedType(String::class.java, emptyList())
                    )
                )
            )
        )

        assertEquals(DataType.OBJECT, result.type)
        assertNull(result.format)
        with(result as SchemaObjectModel) {
            with(properties["recursion"] as SchemaRefObjectModel) {
                // object because generic types are not evaluated
                assertEquals("Object", ref)
            }
        }
    }

    @Test
    fun `build withJsonIgnoreFields successfullyIgnores`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val builder = SchemaObjectBuilder(provider, schemaModelContextHolder)

        val result = builder.build(ClassWithIgnoredFields::class.java, emptyList())

        assertEquals(DataType.OBJECT, result.type)
        assertNull(result.format)
        with(result as SchemaObjectModel) {
            assertEquals(2, properties.size)
        }
    }

    class RecursiveClass {
        val recursion: RecursiveClass = this
    }

    class DeepRecursiveClass {
        val value: NestedClass = NestedClass()
    }

    class NestedClass {
        val recursion: DeepRecursiveClass? = null
    }

    class RecursiveList {
        val recursion: List<RecursiveList> = emptyList()
    }

    class RecursiveGenericClass<T> {
        val recursion: T? = null
    }

    class ClassWithIgnoredFields {
        lateinit var field1: String

        @JsonIgnore
        lateinit var field2: String

        @JsonIgnore
        lateinit var recursiveClass: RecursiveClass
        var field3: Int = 0
    }
}