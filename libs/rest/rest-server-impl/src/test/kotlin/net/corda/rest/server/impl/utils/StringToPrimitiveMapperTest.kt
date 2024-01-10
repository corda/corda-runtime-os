package net.corda.rest.server.impl.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class StringToPrimitiveMapperTest {
    private enum class TestEnum {
        ONE, TWO
    }

    @Test
    fun `map_withInt_succeeds`() {
        val result = "12".mapTo(Int::class.java)
        assertEquals(12, result)
    }

    @Test
    fun `map_withLong_succeeds`() {
        val result = "999999999999".mapTo(Long::class.java)
        assertEquals(999999999999L, result)
    }

    @Test
    fun `map_withString_succeeds`() {
        val result = "12".mapTo(String::class.java)
        assertEquals("12", result)
    }

    @Test
    fun `map_withBoolean_succeeds`() {
        val result = "false".mapTo(Boolean::class.java)
        assertEquals(false, result)
    }

    @Test
    fun `map_withDouble_succeeds`() {
        val result = "99999.9999".mapTo(Double::class.java)
        assertEquals(99999.9999, result)
    }

    @Test
    fun `map_withByte_succeeds`() {
        val result = "11".mapTo(Byte::class.java)
        assertEquals("11", result.toString())
    }

    @Test
    fun `map_withFloat_succeeds`() {
        val result = "11.12".mapTo(Float::class.java)
        assertEquals(11.12F, result)
    }

    @Test
    fun `map_withShort_succeeds`() {
        val result = "11".mapTo(Short::class.java)
        assertEquals(11, result)
    }

    @Test
    fun `map_withChar_succeeds`() {
        val result = "1".mapTo(Char::class.java)
        assertEquals('1', result)
    }

    @Test
    fun `map_withEnum_succeeds`() {
        val result = "TWO".mapTo(TestEnum::class.java)
        assertEquals(TestEnum.TWO, result)
    }
}