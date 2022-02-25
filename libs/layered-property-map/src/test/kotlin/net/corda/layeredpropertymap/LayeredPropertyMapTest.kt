package net.corda.layeredpropertymap

import net.corda.layeredpropertymap.impl.LayeredPropertyMapImpl
import net.corda.layeredpropertymap.impl.PropertyConverter
import net.corda.v5.base.exceptions.ValueNotFoundException
import net.corda.v5.base.types.toHexString
import net.corda.v5.base.util.parse
import net.corda.v5.base.util.parseList
import net.corda.v5.base.util.parseOrNull
import net.corda.v5.crypto.PublicKeyHash
import net.corda.v5.crypto.sha256Bytes
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

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
        private val dummyList = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18)

        private fun convertDummyValues(): List<Pair<String, String>> =
            dummyList.mapIndexed { i, value -> String.format(DUMMY_LIST, i) to value.toString() }

        @Suppress("SpreadOperator")
        private fun createLayeredPropertyMapImpl() = LayeredPropertyMapImpl(
            mapOf(
                NUMBER to number.toString(),
                TEXT to text,
                DUMMY_OBJECT_NUMBER to number.toString(),
                DUMMY_OBJECT_TEXT to text,
                MODIFIED_TIME to initialTime.toString(),
                NULL to null,
                *convertDummyValues().toTypedArray(),
                FAILING_DUMMY_OBJECT_NUMBER to number.toString(),
                FAILING_LIST_STRUCTURE to number.toString(),
                "corda.endpoints.2.url" to "localhost3",
                "corda.endpoints.2.protocolVersion" to "3",
                "corda.endpoints.0.url" to "localhost1",
                "corda.endpoints.0.protocolVersion" to "1",
                "corda.endpoints.1.url" to "localhost2",
                "corda.endpoints.1.protocolVersion" to "2",
                "listWithNull.0" to "42",
                "listWithNull.1" to null,
                "singlePublicKeyHash" to "single".toByteArray().sha256Bytes().toHexString(),
                "listPublicKeyHash.0" to "list0".toByteArray().sha256Bytes().toHexString(),
                "listPublicKeyHash.1" to "list1".toByteArray().sha256Bytes().toHexString(),
                "listPublicKeyHash.2" to "list2".toByteArray().sha256Bytes().toHexString(),
            ),
            PropertyConverter(
                mapOf(
                    DummyObjectWithNumberAndText::class.java to DummyConverter(),
                    DummyEndpointInfo::class.java to DummyEndpointInfoConverter(),
                    PublicKeyHash::class.java to DummyPublicKeyHashConverter()
                )
            )
        )
    }

    @Test
    fun `converter functions should work for custom converter of single value`() {
        val propertyMap = createLayeredPropertyMapImpl()
        val single1 = propertyMap.parse<PublicKeyHash>("singlePublicKeyHash")
        val single2 = propertyMap.parseOrNull<PublicKeyHash>("singlePublicKeyHash")
        assertEquals(single1, single2)
        assertEquals("single".toByteArray().sha256Bytes().toHexString(), single1.value)
        val list = propertyMap.parseList<PublicKeyHash>("listPublicKeyHash")
        assertEquals(3, list.size)
        assertEquals("list0".toByteArray().sha256Bytes().toHexString(), list[0].value)
        assertEquals("list1".toByteArray().sha256Bytes().toHexString(), list[1].value)
        assertEquals("list2".toByteArray().sha256Bytes().toHexString(), list[2].value)
    }

    @Test
    fun `converter functions should work`() {
        val propertyMap = createLayeredPropertyMapImpl()
        assertEquals(38, propertyMap.entries.size)
        assertEquals(number, propertyMap.parse(NUMBER))
        assertEquals(text, propertyMap.parse(TEXT))
        assertEquals(initialTime, propertyMap.parse(MODIFIED_TIME))
        assertEquals(null, propertyMap.parseOrNull<String>(NULL))
        assertEquals(null, propertyMap.parseOrNull<String>(KEY_WITHOUT_VALUE))
    }

    @Test
    fun `converter functions should work second time around by fetching from cache`() {
        val propertyMap = createLayeredPropertyMapImpl()
        assertEquals(38, propertyMap.entries.size)
        assertEquals(number, propertyMap.parse(NUMBER))
        assertEquals(number, propertyMap.parse(NUMBER))
        assertEquals(text, propertyMap.parse(TEXT))
        assertEquals(text, propertyMap.parse(TEXT))
        assertEquals(initialTime, propertyMap.parse(MODIFIED_TIME))
        assertEquals(initialTime, propertyMap.parse(MODIFIED_TIME))
        assertEquals(null, propertyMap.parseOrNull<String>(NULL))
        assertEquals(null, propertyMap.parseOrNull<String>(NULL))
        assertEquals(null, propertyMap.parseOrNull<String>(KEY_WITHOUT_VALUE))
        assertEquals(null, propertyMap.parseOrNull<String>(KEY_WITHOUT_VALUE))
    }

    @Test
    fun `converter parseOrNull should work second time around by fetching from cache`() {
        val propertyMap = createLayeredPropertyMapImpl()
        assertEquals(38, propertyMap.entries.size)
        assertEquals(number, propertyMap.parseOrNull(NUMBER))
        assertEquals(number, propertyMap.parseOrNull(NUMBER))
        assertEquals(text, propertyMap.parseOrNull(TEXT))
        assertEquals(text, propertyMap.parseOrNull(TEXT))
        assertEquals(initialTime, propertyMap.parseOrNull(MODIFIED_TIME))
        assertEquals(initialTime, propertyMap.parseOrNull(MODIFIED_TIME))
    }

    @Test
    fun `parseList should be able to parse use prefix without trailing dot`() {
        val propertyMap = createLayeredPropertyMapImpl()
        val parsedDummyList = propertyMap.parseList<Int>(DUMMY_LIST_PREFIX)
        assertEquals(dummyList, parsedDummyList)
    }

    @Test
    fun `parseList should be able to parse use prefix with trailing dot`() {
        val propertyMap = createLayeredPropertyMapImpl()
        val parsedDummyList = propertyMap.parseList<Int>("$DUMMY_LIST_PREFIX.")
        assertEquals(dummyList, parsedDummyList)
    }

    @Test
    fun `parseList should be able to parse second time around by fetching from cache`() {
        val propertyMap = createLayeredPropertyMapImpl()
        val parsedDummyList1 = propertyMap.parseList<Int>(DUMMY_LIST_PREFIX)
        assertEquals(dummyList, parsedDummyList1)
        val parsedDummyList2 = propertyMap.parseList<Int>(DUMMY_LIST_PREFIX)
        assertSame(parsedDummyList1, parsedDummyList2)
    }

    @Test
    fun `parseList should be able to parse complex objects`() {
        val propertyMap = createLayeredPropertyMapImpl()
        val parsedList = propertyMap.parseList<DummyEndpointInfo>("corda.endpoints")
        assertEquals(3, parsedList.size)
        for( i in 0 until  3) {
            assertEquals("localhost${i+1}", parsedList[i].url)
            assertEquals(i+1, parsedList[i].protocolVersion)
        }
    }

    @Test
    fun `parseList should throw ValueNotFoundException when one of the items is null`() {
        val propertyMap = createLayeredPropertyMapImpl()
        assertFailsWith<ValueNotFoundException> { propertyMap.parseList<Int>("listWithNull") }
    }

    @Test
    fun `parse fails when null value is used and when key is not in the map`() {
        val propertyMap = createLayeredPropertyMapImpl()
        assertFailsWith<ValueNotFoundException> { propertyMap.parse(KEY_WITHOUT_VALUE) }
        assertFailsWith<ValueNotFoundException> { propertyMap.parse(NULL) }
    }

    @Test
    fun `custom converter fails when not allowed null value is used`() {
        val propertyMap = createLayeredPropertyMapImpl()
        assertFailsWith<NullPointerException> { propertyMap.parse<DummyObjectWithNumberAndText>(FAILING_DUMMY_OBJECT) }
    }

    @Test
    fun `parseList fails when invalid key structure used`() {
        val propertyMap = createLayeredPropertyMapImpl()
        assertFailsWith<IllegalArgumentException> {
            propertyMap.parseList<Int>(FAILING_LIST_PREFIX)
        }
    }

    @Test
    fun `parse should convert complex value`() {
        val propertyMap = createLayeredPropertyMapImpl()
        val dummyObject = propertyMap.parse<DummyObjectWithNumberAndText>(DUMMY_OBJECT)
        assertEquals(number, dummyObject.number)
        assertEquals(text, dummyObject.text)
    }

    @Test
    fun `parseOrNull should convert complex value`() {
        val propertyMap = createLayeredPropertyMapImpl()
        val dummyObject = propertyMap.parseOrNull<DummyObjectWithNumberAndText>(DUMMY_OBJECT)
        assertNotNull(dummyObject)
        assertEquals(number, dummyObject.number)
        assertEquals(text, dummyObject.text)
    }

    @Test
    fun `parse and fails when invalid casting occurs for cached value`() {
        val propertyMap = createLayeredPropertyMapImpl()
        val dummyObject = propertyMap.parse<DummyObjectWithNumberAndText>(DUMMY_OBJECT)
        assertEquals(number, dummyObject.number)
        assertEquals(text, dummyObject.text)

        assertFailsWith<ClassCastException> { propertyMap.parse<Instant>(DUMMY_OBJECT) }
        assertFailsWith<ClassCastException> { propertyMap.parse<Instant>(DUMMY_OBJECT) }
    }

    @Test
    fun `parseOrNull and fails when invalid casting occurs for cached value`() {
        val propertyMap = createLayeredPropertyMapImpl()
        val dummyObject = propertyMap.parseOrNull<DummyObjectWithNumberAndText>(DUMMY_OBJECT)
        assertEquals(number, dummyObject!!.number)
        assertEquals(text, dummyObject.text)

        assertFailsWith<ClassCastException> { propertyMap.parseOrNull<Instant>(DUMMY_OBJECT) }
        assertFailsWith<ClassCastException> { propertyMap.parseOrNull<Instant>(DUMMY_OBJECT) }
    }

    @Test
    fun `parseList fails when invalid casting occurs`() {
        val propertyMap = createLayeredPropertyMapImpl()
        val parsedDummyList = propertyMap.parseList<Int>(DUMMY_LIST_PREFIX)
        assertEquals(dummyList, parsedDummyList)

        assertFailsWith<ClassCastException> { propertyMap.parseList<Instant>(DUMMY_LIST_PREFIX) }
    }

    @Test
    fun `parseList returns empty list when no value was found for key prefix`() {
        val propertyMap = createLayeredPropertyMapImpl()
        val nonExistentKeyPrefix = "nonExistentKeyPrefix"
        assertTrue(propertyMap.parseList<String>(nonExistentKeyPrefix).isEmpty())
    }

    @Test
    fun `hashCode should return same value for equal maps`() {
        val map1 = createLayeredPropertyMapImpl()
        val map2 = createLayeredPropertyMapImpl()
        assertEquals(map1.hashCode(), map2.hashCode())
    }

    @Test
    fun `hashCode should return same value for same map`() {
        val map1 = createLayeredPropertyMapImpl()
        assertEquals(map1.hashCode(), map1.hashCode())
    }

    @Test
    fun `hashCode should return same value for equal maps with different property converters`() {
        val map1 = LayeredPropertyMapImpl(
            sortedMapOf(
                NUMBER to number.toString(),
                TEXT to text
            ),
            PropertyConverter(
                mapOf(
                    DummyObjectWithNumberAndText::class.java to DummyConverter()
                )
            )
        )
        val map2 = LayeredPropertyMapImpl(
            sortedMapOf(
                TEXT to text,
                NUMBER to number.toString()
            ),
            PropertyConverter(emptyMap())
        )
        assertEquals(map1.hashCode(), map2.hashCode())
    }

    @Test
    fun `hashCode should return different value for non equal maps`() {
        val map1 = createLayeredPropertyMapImpl()
        val map2 = LayeredPropertyMapImpl(
            sortedMapOf(
                NUMBER to number.toString(),
                TEXT to text
            ),
            PropertyConverter(
                mapOf(
                    DummyObjectWithNumberAndText::class.java to DummyConverter()
                )
            )
        )
        assertNotEquals(map1.hashCode(), map2.hashCode())
    }

    @Test
    fun `equals should return true for equal maps`() {
        val map1 = createLayeredPropertyMapImpl()
        val map2 = createLayeredPropertyMapImpl()
        assertEquals(map1, map2)
    }

    @Test
    fun `equals should return true for the same map`() {
        val map1 = createLayeredPropertyMapImpl()
        assertEquals(map1, map1)
    }

    @Test
    fun `equals should return false if other map is null`() {
        val map1 = createLayeredPropertyMapImpl()
        assertFalse(map1.equals(null))
    }

    @Test
    fun `equals should return false if other map is not layered map`() {
        val map1 = createLayeredPropertyMapImpl()
        val map2 = emptyMap<String, String>()
        assertFalse(map1.equals(map2))
    }

    @Test
    fun `equals should return true for equal maps with different property converters`() {
        val map1 = LayeredPropertyMapImpl(
            sortedMapOf(
                NUMBER to number.toString(),
                TEXT to text
            ),
            PropertyConverter(
                mapOf(
                    DummyObjectWithNumberAndText::class.java to DummyConverter()
                )
            )
        )
        val map2 = LayeredPropertyMapImpl(
            sortedMapOf(
                TEXT to text,
                NUMBER to number.toString()
            ),
            PropertyConverter(emptyMap())
        )
        assertEquals(map1, map2)
    }

    @Test
    fun `equals should return false for non equal maps`() {
        val map1 = createLayeredPropertyMapImpl()
        val map2 = LayeredPropertyMapImpl(
            sortedMapOf(
                NUMBER to number.toString(),
                TEXT to text
            ),
            PropertyConverter(
                mapOf(
                    DummyObjectWithNumberAndText::class.java to DummyConverter()
                )
            )
        )
        assertNotEquals(map1, map2)
    }

    @Test
    fun `parse should throw IllegalArgumentException when the key is blank`() {
        val propertyMap = createLayeredPropertyMapImpl()
        assertFailsWith<IllegalArgumentException> { propertyMap.parse<String>("") }
    }

    @Test
    fun `parseOrNull should throw IllegalArgumentException when the key is blank`() {
        val propertyMap = createLayeredPropertyMapImpl()
        assertFailsWith<IllegalArgumentException> { propertyMap.parseOrNull<String>("") }
    }

    @Test
    fun `parseList should throw IllegalArgumentException when the itemKeyPrefix is blank`() {
        val propertyMap = createLayeredPropertyMapImpl()
        assertFailsWith<IllegalArgumentException> { propertyMap.parseList<String>("") }
    }
}