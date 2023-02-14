package net.corda.layeredpropertymap

import net.corda.layeredpropertymap.impl.LayeredPropertyMapImpl
import net.corda.layeredpropertymap.impl.PropertyConverter
import net.corda.v5.base.types.MemberX500Name
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PropertyConverterTest {
    companion object {
        private val propertyConverter = PropertyConverter(emptyMap())

        private const val INT_KEY = "int"
        private const val INT_VALUE = 1

        private const val LONG_KEY = "long"
        private const val LONG_VALUE = INT_VALUE.toLong()

        private const val SHORT_KEY = "short"
        private const val SHORT_VALUE = INT_VALUE.toShort()

        private const val FLOAT_KEY = "float"
        private const val FLOAT_VALUE = INT_VALUE.toFloat()

        private const val DOUBLE_KEY = "double"
        private const val DOUBLE_VALUE = INT_VALUE.toDouble()

        private const val STRING_KEY = "string"
        private const val STRING_VALUE = "value"

        private const val INSTANT_KEY = "instant"
        private val INSTANT_VALUE = Instant.now()

        private const val MEMBERX500NAME_KEY = "memberX500Name"
        private val MEMBERX500NAME_VALUE = MemberX500Name.parse("O=Alice,L=London,C=GB")

        private const val UUID_KEY = "uuid"
        private val UUID_VALUE = UUID(0, 1)

        private val layeredPropertyMapImpl = LayeredPropertyMapImpl(
            sortedMapOf(
                INT_KEY to INT_VALUE.toString(),
                LONG_KEY to LONG_VALUE.toString(),
                SHORT_KEY to SHORT_VALUE.toString(),
                FLOAT_KEY to FLOAT_VALUE.toString(),
                DOUBLE_KEY to DOUBLE_VALUE.toString(),
                STRING_KEY to STRING_VALUE,
                INSTANT_KEY to INSTANT_VALUE.toString(),
                MEMBERX500NAME_KEY to MEMBERX500NAME_VALUE.toString(),
                UUID_KEY to UUID_VALUE.toString(),
            ),
            propertyConverter
        )

        private fun createContext(key: String) = ConversionContext(
            layeredPropertyMapImpl,
            key
        )
    }

    @Test
    fun `converting int should work`() {
        val context = createContext(INT_KEY)
        assertEquals(INT_VALUE, propertyConverter.convert(context, Int::class.java))
    }

    @Test
    fun `converting long should work`() {
        val context = createContext(LONG_KEY)
        assertEquals(LONG_VALUE, propertyConverter.convert(context, Long::class.java))
    }

    @Test
    fun `converting short should work`() {
        val context = createContext(SHORT_KEY)
        assertEquals(SHORT_VALUE, propertyConverter.convert(context, Short::class.java))
    }

    @Test
    fun `converting float should work`() {
        val context = createContext(FLOAT_KEY)
        assertEquals(FLOAT_VALUE, propertyConverter.convert(context, Float::class.java))
    }

    @Test
    fun `converting double should work`() {
        val context = createContext(DOUBLE_KEY)
        assertEquals(DOUBLE_VALUE, propertyConverter.convert(context, Double::class.java))
    }

    @Test
    fun `converting string should work`() {
        val context = createContext(STRING_KEY)
        assertEquals(STRING_VALUE, propertyConverter.convert(context, String::class.java))
    }

    @Test
    fun `converting instant should work`() {
        val context = createContext(INSTANT_KEY)
        assertEquals(INSTANT_VALUE, propertyConverter.convert(context, Instant::class.java))
    }

    @Test
    fun `converting MemberX500Name should work`() {
        val context = createContext(MEMBERX500NAME_KEY)
        assertEquals(MEMBERX500NAME_VALUE, propertyConverter.convert(context, MemberX500Name::class.java))
    }

    @Test
    fun `converting UUID should work`() {
        val context = createContext(UUID_KEY)
        assertEquals(UUID_VALUE, propertyConverter.convert(context, UUID::class.java))
    }

    @Test
    fun `converter should return null when there is no value for the key`() {
        val nullContext = createContext("null")
        assertNull(propertyConverter.convert(nullContext, String::class.java))
    }

    @Test
    fun `converter should fail when value cannot be parsed to given type`() {
        val context = createContext(STRING_KEY)
        assertFailsWith<DateTimeParseException> { propertyConverter.convert(context, Instant::class.java) }
    }
}