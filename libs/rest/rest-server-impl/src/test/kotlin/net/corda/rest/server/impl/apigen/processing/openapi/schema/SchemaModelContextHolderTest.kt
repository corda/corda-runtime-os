package net.corda.rest.server.impl.apigen.processing.openapi.schema

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

internal class SchemaModelContextHolderTest {
    @Test
    fun `markDiscovered succeeds`() {
        val schemaModelContextHolder = SchemaModelContextHolder()
        val data = ParameterizedClass(String::class.java)
        schemaModelContextHolder.markDiscovered(data)
        assertNotNull(schemaModelContextHolder.getName(data))
    }
}