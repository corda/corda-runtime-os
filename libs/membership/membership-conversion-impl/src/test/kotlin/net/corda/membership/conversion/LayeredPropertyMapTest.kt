package net.corda.membership.conversion

import net.corda.membership.testkit.DummyConverter
import net.corda.membership.testkit.DummyObjectWithNumberAndText
import net.corda.v5.membership.conversion.ValueNotFoundException
import net.corda.v5.membership.conversion.parseList
import org.junit.jupiter.api.Test
import java.lang.ClassCastException
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LayeredPropertyMapTest {
    companion object {
        private const val NUMBER = "number"
        private const val number = 5
        private const val TEXT = "text"
        private const val text = "Hello World"
        private const val DUMMY_OBJECT = "dummyObject"
        private const val DUMMY_OBJECT_NUMBER = "dummyObject.number"
        private const val DUMMY_OBJECT_TEXT = "dummyObject.text"
        private const val FAILING_DUMMY_OBJECT = "failingDummyObject"
        private const val FAILING_DUMMY_OBJECT_NUMBER = "failingDummyObject.number"
        private const val FAILING_LIST_PREFIX = "failingList"
        private const val FAILING_LIST_STRUCTURE = "failingList.number"
        private const val MODIFIED_TIME = "modifiedTime"
        private val initialTime = Instant.now()
        private const val NULL = "null"
        private const val KEY_WITHOUT_VALUE = "keyWithoutValue"
        private const val DUMMY_LIST_PREFIX = "dummyList"
        private const val DUMMY_LIST = "dummyList.%s"
        private val dummyList = listOf(1, 2, 3, 4, 5)

        private fun convertDummyValues(): List<Pair<String, String>> =
            dummyList.mapIndexed { i, value -> String.format(DUMMY_LIST, i) to value.toString() }

        @Suppress("SpreadOperator")
        private val propertyMap = LayeredPropertyMapImpl(
            sortedMapOf(
                NUMBER to number.toString(),
                TEXT to text,
                DUMMY_OBJECT_NUMBER to number.toString(),
                DUMMY_OBJECT_TEXT to text,
                MODIFIED_TIME to initialTime.toString(),
                NULL to null,
                *convertDummyValues().toTypedArray(),
                FAILING_DUMMY_OBJECT_NUMBER to number.toString(),
                FAILING_LIST_STRUCTURE to number.toString()
            ),
            PropertyConverterImpl(listOf(DummyConverter()))
        )
    }

    @Test
    fun `converter functions should work`() {
        assertEquals(13, propertyMap.entries.size)
        assertEquals(number, propertyMap.parse(NUMBER))
        assertEquals(text, propertyMap.parse(TEXT))
        assertEquals(initialTime, propertyMap.parse(MODIFIED_TIME))
        assertEquals(null, propertyMap.parseOrNull<String>(NULL))
        assertEquals(null, propertyMap.parseOrNull<String>(KEY_WITHOUT_VALUE))
    }

    @Test
    fun `parse fails when null value is used and when key is not in the map`() {
        val exWhenKeyIsNotInTheMap = assertFailsWith<ValueNotFoundException> { propertyMap.parse(KEY_WITHOUT_VALUE) }
        assertEquals("There is no value for '$KEY_WITHOUT_VALUE' key.", exWhenKeyIsNotInTheMap.message)

        val exWhenValueIsNull = assertFailsWith<IllegalStateException> { propertyMap.parse(NULL) }
        assertEquals("Converted value cannot be null.", exWhenValueIsNull.message)
    }

    @Test
    fun `custom converter fails when not allowed null value is used`() {
        assertFailsWith<NullPointerException> { propertyMap.parse<DummyObjectWithNumberAndText>(FAILING_DUMMY_OBJECT) }
    }

    @Test
    fun `parseList fails when invalid key structure used`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            propertyMap.parseList<Int>(FAILING_LIST_PREFIX)
        }
        assertEquals("Prefix is invalid, only number is accepted after prefix.", ex.message)
    }

    @Test
    fun `parse and parseOrNull fails when invalid casting occurs`() {
        val dummyObject = propertyMap.parse<DummyObjectWithNumberAndText>(DUMMY_OBJECT)
        assertEquals(number, dummyObject.number)
        assertEquals(text, dummyObject.text)

        val exForParse = assertFailsWith<ClassCastException> { propertyMap.parse<Instant>(DUMMY_OBJECT) }
        assertEquals("Casting failed for $DUMMY_OBJECT.", exForParse.message)

        val exForParseOrNull = assertFailsWith<ClassCastException> { propertyMap.parse<Instant>(DUMMY_OBJECT) }
        assertEquals("Casting failed for $DUMMY_OBJECT.", exForParseOrNull.message)
    }

    @Test
    fun `parseList fails when invalid casting occurs`() {
        val parsedDummyList = propertyMap.parseList<Int>(DUMMY_LIST_PREFIX)
        assertEquals(dummyList, parsedDummyList)

        val ex = assertFailsWith<ClassCastException> { propertyMap.parseList<Instant>(DUMMY_LIST_PREFIX) }
        assertEquals("Casting failed for $DUMMY_LIST_PREFIX prefix.", ex.message)
    }

    @Test
    fun `parseList throws ValueNotFoundException when no value was found for key prefix`() {
        val nonExistentKeyPrefix = "nonExistentKeyPrefix"
        val ex = assertFailsWith<ValueNotFoundException> { propertyMap.parseList<String>(nonExistentKeyPrefix) }
        assertEquals("There is no value for '$nonExistentKeyPrefix' prefix.", ex.message)
    }
}