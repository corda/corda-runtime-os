package net.corda.flow.utils

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class KeyValueStoreTest {
    companion object {
        val KEY_VALUE_PAIR = KeyValuePair("key", "value")
    }

    data class Cat(val name: String, val breed: String)

    @Test
    fun `mutableKeyValuePairList as a parameter to a container can be modified`() {
        data class Container(val list: KeyValuePairList)

        val containerBackingList = mutableKeyValuePairList()
        val container = Container(containerBackingList)
        container.list.items.add(KEY_VALUE_PAIR)

        assertThat(containerBackingList.items).contains(KEY_VALUE_PAIR)
    }

    @Test
    fun `emptyKeyValuePairList creates an empty immutable list`() {
        val immutableKeyValueStoreList = emptyKeyValuePairList()
        assertThat(immutableKeyValueStoreList.items.size).isEqualTo(0)
        assertThrows<UnsupportedOperationException> { immutableKeyValueStoreList.items.add(KEY_VALUE_PAIR) }
    }

    @Test
    fun `KeyValueStore set and get`() {
        val backingList = mutableKeyValuePairList()
        val store = KeyValueStore(backingList)
        store.set("key1", "value1")
        store.put("key2", "value2")
        store["key3"] = "value3"

        assertThat(store.get("key1")).isEqualTo("value1")
        assertThat(store.get("key2")).isEqualTo("value2")
        assertThat(store.get("key3")).isEqualTo("value3")

        assertThat(store["key1"]).isEqualTo("value1")
        assertThat(store["key2"]).isEqualTo("value2")
        assertThat(store["key3"]).isEqualTo("value3")

        assertThat(store["key4"]).isNull()

        store["key2"] = "value2-modified"

        assertThat(store["key1"]).isEqualTo("value1")
        assertThat(store["key2"]).isEqualTo("value2-modified")
        assertThat(store["key3"]).isEqualTo("value3")

        assertThat(backingList.items.size).isEqualTo(3)
    }

    @Test
    fun `KeyValueStore avro returns reference to avro array`() {
        val backingList = mutableKeyValuePairList()
        val store = KeyValueStore(backingList)

        assertTrue(store.avro === backingList)
    }

    @Test
    fun `keyValueStoreOf creates store`() {
        val store = keyValueStoreOf("key1" to "value1", "key2" to "value2")

        assertThat(store["key1"]).isEqualTo("value1")
        assertThat(store["key2"]).isEqualTo("value2")

        assertThat(store.avro.items.size).isEqualTo(2)
    }

    @Test
    fun `mutableKeyValuePairList with initial properties`() {
        val store = keyValueStoreOf("key1" to "value1", "key2" to "value2")
        val initialKeyValuePairList = store.avro

        val mutableKeyValuePairList = mutableKeyValuePairListOf(initialKeyValuePairList)

        // Check initial items appear in the new list
        assertThat(mutableKeyValuePairList.items[0]).isEqualTo(KeyValuePair("key1", "value1"))
        assertThat(mutableKeyValuePairList.items[1]).isEqualTo(KeyValuePair("key2", "value2"))
        assertThat(mutableKeyValuePairList.items.size).isEqualTo(2)

        // Check the list is mutable
        mutableKeyValuePairList.items.add(KEY_VALUE_PAIR)
        assertThat(mutableKeyValuePairList.items[2]).isEqualTo(KEY_VALUE_PAIR)
        assertThat(mutableKeyValuePairList.items.size).isEqualTo(3)

        // Check the initial list is unchanged, i.e. items were cloned into it rather than a reference to the initial
        // list was taken
        assertThat(initialKeyValuePairList.items.size).isEqualTo(2)
    }

    @Test
    fun `keyValuePairListOf creates list from map`() {
        val map = mapOf("key1" to "value1", "key2" to "value2")
        val keyValuePairList = keyValuePairListOf(map)

        // Back the list by a store facade for convenience of testing the values in it
        val store = KeyValueStore(keyValuePairList)
        assertThat(store["key1"]).isEqualTo("value1")
        assertThat(store["key2"]).isEqualTo("value2")

        assertThat(keyValuePairList.items.size).isEqualTo(2)
    }

    @Test
    fun `toMutableMap creates a mutable map representation of the keyValuePairList`() {
        val map = mapOf("key1" to "value1", "key2" to "value2", "key3" to "value3")
        val keyValuePairList = keyValuePairListOf(map)

        val keyValuePairListAsMap = keyValuePairList.toMutableMap()
        assertThat(keyValuePairListAsMap).isInstanceOf(MutableMap::class.java).isEqualTo(map)
        assertThat(keyValuePairListAsMap.remove("key3", "value3")).isTrue
    }

    @Test
    fun `test toKeyValuePairList with default prefix`() {
        val cats = listOf(
            Cat("Garfield", "Tabby"),
            Cat("Sylvester", "Tuxedo"),
            Cat("Tom", "Domestic Shorthair")
        )
        val expected = KeyValuePairList(
            listOf(
                KeyValuePair("Cat.0", "Cat(name=Garfield, breed=Tabby)"),
                KeyValuePair("Cat.1", "Cat(name=Sylvester, breed=Tuxedo)"),
                KeyValuePair("Cat.2", "Cat(name=Tom, breed=Domestic Shorthair)")
            )
        )
        val actual = cats.toKeyValuePairList()
        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun `test toKeyValuePairList with custom prefix`() {
        val colors = listOf("orange", "black", "gray")
        val expected = KeyValuePairList(
            listOf(
                KeyValuePair("myCats.0", "orange"),
                KeyValuePair("myCats.1", "black"),
                KeyValuePair("myCats.2", "gray")
            )
        )
        val actual = colors.toKeyValuePairList("myCats")
        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun `test toKeyValuePairList with null elements`() {
        val colors = listOf("orange", null, "gray")
        val expected = KeyValuePairList(
            listOf(
                KeyValuePair("String.0", "orange"),
                KeyValuePair("String.1", "gray")
            )
        )
        val actual = colors.toKeyValuePairList()
        assertThat(actual).isEqualTo(expected)
    }
}
