package net.corda.libs.json.validator.impl

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class JsonValidatorImplTest {
    private val json = """
       {    "name":   "Jimmy", "age" : 257
       }
       """.trimIndent()

    private val validator = JsonValidatorImpl()
    private val schema = validator.parseSchema(this::class.java.getResourceAsStream("/schema/simple.json"))

    @Test
    fun `canonicalize works on sample input`() {
        val canonical = validator.canonicalize(json)

        assertEquals("""{"age":257,"name":"Jimmy"}""", canonical)
    }

    @Test
    fun `Valid, canonical JSON is accepted`() {
        val goodJson = validator.canonicalize(json)
        assertDoesNotThrow {validator.validate(goodJson, schema)}
    }

    @Test
    fun `Valid, non-canonical JSON is rejected`() {
        assertThrows<IllegalStateException> {validator.validate(json, schema)}
    }

    @Test
    fun `Invalid, canonical JSON is rejected`() {
        assertThrows<IllegalStateException> {
            validator.validate("""{"age":1,"name":1}""", schema)
        }
    }
}