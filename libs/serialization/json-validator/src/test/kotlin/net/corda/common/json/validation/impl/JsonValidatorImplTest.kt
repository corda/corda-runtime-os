package net.corda.common.json.validation.impl

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class JsonValidatorImplTest {
    private val json = """
       {    "name":   "Jimmy", "age" : 257
       }
       """.trimIndent()
    private val schemaPath = "/schema/simple.json"

    @Test
    fun `canonicalize works on sample input`() {
        val validator = JsonValidatorImpl()
        val canonical = validator.canonicalize(json)

        assertEquals("""{"age":257,"name":"Jimmy"}""", canonical)
    }

    @Test
    fun `Valid, canonical JSON is accepted`() {
        val validator = JsonValidatorImpl()
        val goodJson = validator.canonicalize(json)
        assertDoesNotThrow {validator.validate(goodJson, schemaPath)}
    }

    @Test
    fun `Valid, non-canonical JSON is rejected`() {
        val validator = JsonValidatorImpl()
        assertThrows<IllegalStateException> {validator.validate(json, schemaPath)}
    }

    @Test
    fun `Invalid, canonical JSON is rejected`() {
        val validator = JsonValidatorImpl()
        assertThrows<IllegalStateException> {validator.validate("""{"age":1,"name":1}""", schemaPath)}
    }
}