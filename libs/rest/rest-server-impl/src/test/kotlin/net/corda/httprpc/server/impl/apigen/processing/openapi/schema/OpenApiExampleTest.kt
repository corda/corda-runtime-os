package net.corda.httprpc.server.impl.apigen.processing.openapi.schema

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.time.Instant

class OpenApiExampleTest {

    @Test
    fun `Generated Instant example can be parsed as Instant`() {
        val generatedExample = Instant.now()::class.java.toExample().toString()
        assertDoesNotThrow { Instant.parse(generatedExample) }
    }
}