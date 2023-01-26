package net.corda.httprpc.server.impl.apigen.processing.openapi.schema.builders

import net.corda.httprpc.server.impl.apigen.models.GenericParameterizedType
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.DefaultSchemaModelProvider
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.SchemaModelContextHolder
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.DataType
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.SchemaPairModel
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.SchemaRefObjectModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class SchemaPairBuilderTest {

    @Test
    fun `build_withPair_succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val builder = SchemaPairBuilder(provider)
        val result = builder.build(
            Pair::class.java,
            listOf(GenericParameterizedType(Int::class.java, emptyList()), GenericParameterizedType(String::class.java, emptyList()))
        )

        assertEquals(DataType.OBJECT, result.type)
        assertNull(result.format)
        with(result as SchemaPairModel) {
            assertEquals(DataType.INTEGER, properties["first"]!!.type)
            assertEquals(DataType.STRING, properties["second"]!!.type)
        }
    }

    @Test
    fun `build_withPairAndNoGenericTypesAvailable_succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val builder = SchemaPairBuilder(provider)
        val result = builder.build(Pair::class.java, emptyList())

        assertEquals(DataType.OBJECT, result.type)
        assertNull(result.format)
        with(result as SchemaPairModel) {
            assertNull(properties["first"]!!.type)
            assertEquals("Object", (properties["first"]!! as SchemaRefObjectModel).ref)
            assertNull(properties["second"]!!.type)
            assertEquals("Object", (properties["second"]!! as SchemaRefObjectModel).ref)
        }
    }

    @Test
    fun `build_withPairAndOneGenericTypesAvailable_succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val builder = SchemaPairBuilder(provider)
        val result = builder.build(Pair::class.java, listOf(GenericParameterizedType(String::class.java, emptyList())))

        assertEquals(DataType.OBJECT, result.type)
        assertNull(result.format)
        with(result as SchemaPairModel) {
            assertNull(properties["first"]!!.type)
            assertEquals("Object", (properties["first"]!! as SchemaRefObjectModel).ref)
            assertNull(properties["second"]!!.type)
            assertEquals("Object", (properties["second"]!! as SchemaRefObjectModel).ref)
        }
    }

    @Test
    fun `build_withNestedPairs_succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val provider = DefaultSchemaModelProvider(schemaModelContextHolder)
        val builder = SchemaPairBuilder(provider)
        val result = builder.build(
            Pair::class.java, listOf(
                GenericParameterizedType(
                    Pair::class.java,
                    listOf(
                        GenericParameterizedType(String::class.java, emptyList()),
                        GenericParameterizedType(Int::class.java, emptyList())
                    )
                ),
                GenericParameterizedType(String::class.java, emptyList())
            )
        )

        assertEquals(DataType.OBJECT, result.type)
        assertNull(result.format)
        with(result as SchemaPairModel) {
            assertEquals(DataType.OBJECT, properties["first"]!!.type)
            with(properties["first"]!! as SchemaPairModel) {
                assertEquals(DataType.STRING, properties["first"]!!.type)
                assertEquals(DataType.INTEGER, properties["second"]!!.type)
            }
            assertEquals(DataType.STRING, properties["second"]!!.type)
        }
    }
}