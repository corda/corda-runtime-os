package net.corda.httprpc.server.impl.apigen.processing.openapi.schema

import io.swagger.v3.oas.models.media.ArraySchema
import io.swagger.v3.oas.models.media.Schema
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.DataType
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.JsonSchemaModel
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.SchemaCollectionModel
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.SchemaEnumModel
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.SchemaMapModel
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.SchemaModel
import net.corda.httprpc.server.impl.apigen.processing.openapi.schema.model.SchemaPairModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class SchemaModelToOpenApiSchemaConverterTest {
    @Test
    fun `convert withEnumSchemaModel succeeds`() {
        val model = SchemaEnumModel(
            enum = listOf("A", "B", "C")
        )

        val result = SchemaModelToOpenApiSchemaConverter.convert(model)

        assertEquals(3, result.enum.size)
        assertEquals("A", result.enum.first())
    }

    @Test
    fun `convert withMapSchemaModel succeeds`() {
        val model = SchemaMapModel(
            additionalProperties = SchemaModel(DataType.STRING, null)
        )

        val result = SchemaModelToOpenApiSchemaConverter.convert(model)

        assertEquals("string", (result.additionalProperties as Schema<*>).type)
        assertEquals(null, (result.additionalProperties as Schema<*>).format)
    }

    @Test
    fun `convert withMapSchemaModelWithNullProperties succeeds`() {
        val model = SchemaMapModel(
            additionalProperties = null
        )

        val result = SchemaModelToOpenApiSchemaConverter.convert(model)

        assertEquals(null, result.additionalProperties)
    }

    @Test
    fun `convert withPairModel succeeds`() {
        val model = SchemaPairModel(
            properties = mapOf(
                "first" to SchemaModel(),
                "second" to SchemaModel()
            )
        )

        val result = SchemaModelToOpenApiSchemaConverter.convert(model)

        assertEquals(2, result.properties.size)
    }

    @Test
    fun `convert withCollectionTypeWithNullItems succeeds`() {
        val model = SchemaCollectionModel(
            items = null
        )

        val result = SchemaModelToOpenApiSchemaConverter.convert(model)

        assertNull((result as ArraySchema).items)
    }

    @Test
    fun `convert withEnumTypeWithNullItems succeeds`() {
        val model = SchemaEnumModel(
            enum = null
        )

        val result = SchemaModelToOpenApiSchemaConverter.convert(model)

        assertNull(result.enum)
    }

    @Test
    fun `convert JsonObject succeeds`() {
        val model = JsonSchemaModel()

        val result = SchemaModelToOpenApiSchemaConverter.convert(model)

        assertEquals("{\"command\":\"echo\", \"data\":{\"value\": \"hello-world\"}}", result.example)
        assertEquals("Can be any value - string, number, boolean, array or object.", result.description)
    }
}