package net.corda.layeredpropertymap

import net.corda.layeredpropertymap.impl.LayeredPropertyMapImpl
import net.corda.layeredpropertymap.impl.PropertyConverter
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PropertyConverterWithCustomConvertersTest {
    companion object {
        private const val INT_KEY = "int"
        private const val INT_VALUE = 1
        private const val INT_CUSTOM_VALUE = 1

        private const val LONG_KEY = "long"
        private const val LONG_VALUE = INT_VALUE.toLong()

        private const val DUMMY_OBJECT = "dummyObject"
        private const val DUMMY_OBJECT_NUMBER = "dummyObject.number"
        private const val DUMMY_OBJECT_TEXT = "dummyObject.text"
        private const val NUMBER_VALUE = 5
        private const val TEXT_VALUE = "Hello World"

        private const val DUMMY_NUMBER = "dummyNumber"

        private val propertyConverter = PropertyConverter(mapOf(
            Int::class.java to object : CustomPropertyConverter<Int> {
                override val type: Class<Int> = Int::class.java
                override fun convert(context: ConversionContext): Int? = INT_CUSTOM_VALUE
            },
            DummyObjectWithNumberAndText::class.java to DummyConverter()
        ))

        private val layeredPropertyMapImpl = LayeredPropertyMapImpl(
            sortedMapOf(
                INT_KEY to INT_VALUE.toString(),
                LONG_KEY to LONG_VALUE.toString(),
                DUMMY_OBJECT_NUMBER to NUMBER_VALUE.toString(),
                DUMMY_OBJECT_TEXT to TEXT_VALUE,
                DUMMY_NUMBER to "7"
            ),
            propertyConverter
        )

        private fun createContext(key: String) = ConversionContext(
            layeredPropertyMapImpl,
            key
        )
    }

    @Test
    fun `converting int should use custom converter`() {
        val context = createContext(INT_KEY)
        assertEquals(INT_CUSTOM_VALUE, propertyConverter.convert(context, Int::class.java))
    }

    @Test
    fun `converting long should still use default converter`() {
        val context = createContext(LONG_KEY)
        assertEquals(LONG_VALUE, propertyConverter.convert(context, Long::class.java))
    }

    @Test
    fun `converting should use provided custom converter`() {
        val context = createContext(DUMMY_OBJECT)
        val dummyObject = propertyConverter.convert(context, DummyObjectWithNumberAndText::class.java)
        assertNotNull(dummyObject)
        assertEquals(NUMBER_VALUE, dummyObject.number)
        assertEquals(TEXT_VALUE, dummyObject.text)
    }

    @Test
    fun `converter should throw IllegalStateException when there is no converter for the expected type`() {
        val context = createContext(DUMMY_NUMBER)
        val ex = assertFailsWith<IllegalStateException> { propertyConverter.convert(context, DummyNumber::class.java) }
        assertNotNull(ex.message)
        assertTrue(ex.message!!.contains(DummyNumber::class.java.name))
    }
}