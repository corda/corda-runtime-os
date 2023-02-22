package net.corda.layeredpropertymap

import net.corda.crypto.cipher.suite.PublicKeyHash
import net.corda.layeredpropertymap.impl.LayeredPropertyMapImpl
import net.corda.layeredpropertymap.impl.PropertyConverter
import net.corda.test.util.createTestCase
import net.corda.utilities.parse
import net.corda.utilities.parseList
import net.corda.utilities.parseOrNull
import net.corda.utilities.parseSet
import net.corda.v5.base.exceptions.ValueNotFoundException
import net.corda.v5.base.types.ByteArrays.toHexString
import net.corda.v5.base.types.LayeredPropertyMap
import net.corda.v5.crypto.sha256Bytes
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
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
        private const val DUMMY_SET_PREFIX = "dummySet"
        private const val DUMMY_SET = "dummySet.%s"
        private val dummySet = setOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18)

        private fun convertDummyListValues(): List<Pair<String, String>> =
            dummyList.mapIndexed { i, value -> String.format(DUMMY_LIST, i) to value.toString() }

        private fun convertDummySetValues(): List<Pair<String, String>> =
            dummySet.mapIndexed { i, value -> String.format(DUMMY_SET, i) to value.toString() }

        @Suppress("SpreadOperator")
        private fun createLayeredPropertyMapImpl() = LayeredPropertyMapImpl(
            mapOf(
                NUMBER to number.toString(),
                TEXT to text,
                DUMMY_OBJECT_NUMBER to number.toString(),
                DUMMY_OBJECT_TEXT to text,
                MODIFIED_TIME to initialTime.toString(),
                NULL to null,
                *convertDummyListValues().toTypedArray(),
                *convertDummySetValues().toTypedArray(),
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
                "singlePublicKeyHash" to toHexString("single".toByteArray().sha256Bytes()),
                "setPublicKeyHash.0" to toHexString("set0".toByteArray().sha256Bytes()),
                "setPublicKeyHash.1" to toHexString("set1".toByteArray().sha256Bytes()),
                "setPublicKeyHash.2" to toHexString("set2".toByteArray().sha256Bytes()),
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
        assertEquals(toHexString("single".toByteArray().sha256Bytes()), single1.toString())
        val set = propertyMap.parseSet<PublicKeyHash>("setPublicKeyHash")
        assertEquals(3, set.size)
        val setContents = set.map { it.value }
        assertTrue(setContents.contains(toHexString("set0".toByteArray().sha256Bytes())))
        assertTrue(setContents.contains(toHexString("set1".toByteArray().sha256Bytes())))
        assertTrue(setContents.contains(toHexString("set2".toByteArray().sha256Bytes())))
    }

    @Test
    fun `converter functions should work`() {
        val propertyMap = createLayeredPropertyMapImpl()
        assertEquals(56, propertyMap.entries.size)
        assertEquals(number, propertyMap.parse(NUMBER))
        assertEquals(text, propertyMap.parse(TEXT))
        assertEquals(initialTime, propertyMap.parse(MODIFIED_TIME))
        assertEquals(null, propertyMap.parseOrNull<String>(NULL))
        assertEquals(null, propertyMap.parseOrNull<String>(KEY_WITHOUT_VALUE))
    }

    @Test
    fun `converter functions should work second time around by fetching from cache`() {
        val propertyMap = createLayeredPropertyMapImpl()
        assertEquals(56, propertyMap.entries.size)
        val number1 = propertyMap.parse<Int>(NUMBER)
        assertEquals(number, number1)
        assertSame(number1, propertyMap.parse(NUMBER))
        val text1 = propertyMap.parse<String>(TEXT)
        assertEquals(text, text1)
        assertSame(text1, propertyMap.parse(TEXT))
        val initialTime1 = propertyMap.parse<Instant>(MODIFIED_TIME)
        assertEquals(initialTime, initialTime1)
        assertSame(initialTime1, propertyMap.parse(MODIFIED_TIME))
        assertEquals(null, propertyMap.parseOrNull<String>(NULL))
        assertEquals(null, propertyMap.parseOrNull<String>(NULL))
        assertEquals(null, propertyMap.parseOrNull<String>(KEY_WITHOUT_VALUE))
        assertEquals(null, propertyMap.parseOrNull<String>(KEY_WITHOUT_VALUE))
    }

    @Test
    fun `converter parseOrNull should work second time around by fetching from cache`() {
        val propertyMap = createLayeredPropertyMapImpl()
        assertEquals(56, propertyMap.entries.size)
        val number1 = propertyMap.parse<Int>(NUMBER)
        assertEquals(number, number1)
        assertSame(number1, propertyMap.parse(NUMBER))
        val text1 = propertyMap.parse<String>(TEXT)
        assertEquals(text, text1)
        assertSame(text1, propertyMap.parse(TEXT))
        val initialTime1 = propertyMap.parse<Instant>(MODIFIED_TIME)
        assertEquals(initialTime, initialTime1)
        assertSame(initialTime1, propertyMap.parse(MODIFIED_TIME))
    }

    @Test
    fun `parseList should be able to parse use prefix without trailing dot`() {
        val propertyMap = createLayeredPropertyMapImpl()
        val parsedDummyList = propertyMap.parseList<Int>(DUMMY_LIST_PREFIX)
        assertEquals(dummyList, parsedDummyList)
    }

    @Test
    fun `parseSet should be able to parse use prefix without trailing dot`() {
        val propertyMap = createLayeredPropertyMapImpl()
        val parsedDummySet = propertyMap.parseSet<Int>(DUMMY_SET_PREFIX)
        assertEquals(dummySet, parsedDummySet)
    }

    @Test
    fun `parseList should be able to parse use prefix with trailing dot`() {
        val propertyMap = createLayeredPropertyMapImpl()
        val parsedDummyList = propertyMap.parseList<Int>("$DUMMY_LIST_PREFIX.")
        assertEquals(dummyList, parsedDummyList)
    }

    @Test
    fun `parseSet should be able to parse use prefix with trailing dot`() {
        val propertyMap = createLayeredPropertyMapImpl()
        val parsedDummySet = propertyMap.parseSet<Int>("$DUMMY_SET_PREFIX.")
        assertEquals(dummySet, parsedDummySet)
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
    fun `parseSet should be able to parse second time around by fetching from cache`() {
        val propertyMap = createLayeredPropertyMapImpl()
        val parsedDummySet1 = propertyMap.parseSet<Int>(DUMMY_SET_PREFIX)
        assertEquals(dummySet, parsedDummySet1)
        val parsedDummySet2 = propertyMap.parseSet<Int>(DUMMY_SET_PREFIX)
        assertSame(parsedDummySet1, parsedDummySet2)
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
    fun `parseSet should be able to parse complex objects`() {
        val propertyMap = createLayeredPropertyMapImpl()
        val parsedSet = propertyMap.parseSet<DummyEndpointInfo>("corda.endpoints")
        assertEquals(3, parsedSet.size)
        val parsedSetContents = parsedSet.toList()
        for( i in 0 until  3) {
            assertEquals("localhost${i+1}", parsedSetContents[i].url)
            assertEquals(i+1, parsedSetContents[i].protocolVersion)
        }
    }

    @Test
    fun `parseList should throw ValueNotFoundException when one of the items is null`() {
        val propertyMap = createLayeredPropertyMapImpl()
        assertFailsWith<ValueNotFoundException> { propertyMap.parseList<Int>("listWithNull") }
    }

    @Test
    fun `parseSet should throw ValueNotFoundException when one of the items is null`() {
        val propertyMap = createLayeredPropertyMapImpl()
        assertFailsWith<ValueNotFoundException> { propertyMap.parseSet<Int>("listWithNull") }
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
    fun `parse should be able return different compatible types for the same key`() {
        val propertyMap = createLayeredPropertyMapImpl()
        val intValue = propertyMap.parse<Int>(NUMBER)
        assertEquals(number, intValue)
        val strValue = propertyMap.parse<String>(NUMBER)
        assertEquals(number, strValue.toInt())
    }

    @Test
    fun `parseOrNull should be able return different compatible types for the same key`() {
        val propertyMap = createLayeredPropertyMapImpl()
        val intValue = propertyMap.parseOrNull<Int>(NUMBER)
        assertNotNull(intValue)
        assertEquals(number, intValue)
        val strValue = propertyMap.parseOrNull<String>(NUMBER)
        assertNotNull(strValue)
        assertEquals(number, strValue.toInt())
    }

    @Test
    fun `parseList should be able return different compatible types for the same prefix`() {
        val propertyMap = createLayeredPropertyMapImpl()
        val intDummyList = propertyMap.parseList<Int>(DUMMY_LIST_PREFIX)
        assertEquals(dummyList, intDummyList)
        val strDummyList = propertyMap.parseList<String>(DUMMY_LIST_PREFIX)
        assertEquals(dummyList, strDummyList.map { it.toInt() })
    }

    @Test
    fun `parseSet should be able return different compatible types for the same prefix`() {
        val propertyMap = createLayeredPropertyMapImpl()
        val intDummySet = propertyMap.parseSet<Int>(DUMMY_SET_PREFIX)
        assertEquals(dummySet, intDummySet)
        val strDummySet = propertyMap.parseSet<String>(DUMMY_SET_PREFIX)
        assertEquals(dummySet, strDummySet.map { it.toInt() }.toSet())
    }

    @Test
    fun `parseList returns empty list when no value was found for key prefix`() {
        val propertyMap = createLayeredPropertyMapImpl()
        val nonExistentKeyPrefix = "nonExistentKeyPrefix"
        assertTrue(propertyMap.parseList<String>(nonExistentKeyPrefix).isEmpty())
    }

    @Test
    fun `parseSet returns empty list when no value was found for key prefix`() {
        val propertyMap = createLayeredPropertyMapImpl()
        val nonExistentKeyPrefix = "nonExistentKeyPrefix"
        assertTrue(propertyMap.parseSet<String>(nonExistentKeyPrefix).isEmpty())
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

    @Test
    fun `parseSet should throw IllegalArgumentException when the itemKeyPrefix is blank`() {
        val propertyMap = createLayeredPropertyMapImpl()
        assertFailsWith<IllegalArgumentException> { propertyMap.parseSet<String>("") }
    }

    @Test
    fun `Should be able to parse sets concurrently by fetching same value from cache`() {
        val mapCount = 1_000
        val threadCount = 20
        val propertyMaps = mutableListOf<LayeredPropertyMap>()
        val results = ConcurrentHashMap<Pair<Int,LayeredPropertyMap>, Set<Int>>()
        (0 until mapCount).forEach { _ ->
            propertyMaps.add(createLayeredPropertyMapImpl())
        }
        (0 until threadCount).createTestCase {
            propertyMaps.forEach { map ->
                results[it to map] = map.parseSet(DUMMY_SET_PREFIX)
            }
        }.runAndValidate()
        propertyMaps.forEach { map ->
            val mapsPerThread = results.filter { it.key.second == map }
            assertEquals(threadCount, mapsPerThread.size)
            val first = mapsPerThread.values.first()
            mapsPerThread.values.forEach { value ->
                assertSame(first, value)
            }
        }
    }

    @Test
    fun `Should be able to parse lists concurrently by fetching same value from cache`() {
        val mapCount = 1_000
        val threadCount = 20
        val propertyMaps = mutableListOf<LayeredPropertyMap>()
        val results = ConcurrentHashMap<Pair<Int,LayeredPropertyMap>, List<Int>>()
        (0 until mapCount).forEach { _ ->
            propertyMaps.add(createLayeredPropertyMapImpl())
        }
        (0 until threadCount).createTestCase {
            propertyMaps.forEach { map ->
                results[it to map] = map.parseList(DUMMY_SET_PREFIX)
            }
        }.runAndValidate()
        propertyMaps.forEach { map ->
            val mapsPerThread = results.filter { it.key.second == map }
            assertEquals(threadCount, mapsPerThread.size)
            val first = mapsPerThread.values.first()
            mapsPerThread.values.forEach { value ->
                assertSame(first, value)
            }
        }
    }

    @Test
    fun `Should be able to parse concurrently by fetching same value from cache`() {
        val mapCount = 1_000
        val threadCount = 20
        val propertyMaps = mutableListOf<LayeredPropertyMap>()
        val results = ConcurrentHashMap<Pair<Int,LayeredPropertyMap>, Instant>()
        (0 until mapCount).forEach { _ ->
            propertyMaps.add(createLayeredPropertyMapImpl())
        }
        (0 until threadCount).createTestCase {
            propertyMaps.forEach { map ->
                results[it to map] = map.parse(MODIFIED_TIME)
            }
        }.runAndValidate()
        propertyMaps.forEach { map ->
            val mapsPerThread = results.filter { it.key.second == map }
            assertEquals(threadCount, mapsPerThread.size)
            val first = mapsPerThread.values.first()
            mapsPerThread.values.forEach { value ->
                assertSame(first, value)
            }
        }
    }

    @Test
    fun `Should be able to parse or null concurrently by fetching same value from cache`() {
        val mapCount = 1_000
        val threadCount = 20
        val propertyMaps = mutableListOf<LayeredPropertyMap>()
        val results = ConcurrentHashMap<Pair<Int,LayeredPropertyMap>, Instant?>()
        (0 until mapCount).forEach { _ ->
            propertyMaps.add(createLayeredPropertyMapImpl())
        }
        (0 until threadCount).createTestCase {
            propertyMaps.forEach { map ->
                results[it to map] = map.parseOrNull(MODIFIED_TIME)
            }
        }.runAndValidate()
        propertyMaps.forEach { map ->
            val mapsPerThread = results.filter { it.key.second == map }
            assertEquals(threadCount, mapsPerThread.size)
            val first = mapsPerThread.values.first()
            mapsPerThread.values.forEach { value ->
                assertSame(first, value)
            }
        }
    }
}