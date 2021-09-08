package net.corda.utilities

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LazyMappedListTest {

    @Test
    fun `LazyMappedList works`() {
        val originalList = (1 until 10).toList()

        var callCounter = 0

        val lazyList = originalList.lazyMapped { value, _ ->
            callCounter++
            value * value
        }

        // No transform called when created.
        assertEquals(0, callCounter)

        // No transform called when calling 'size'.
        assertEquals(9, lazyList.size)
        assertEquals(0, callCounter)

        // Called once when getting an element.
        assertEquals(16, lazyList[3])
        assertEquals(1, callCounter)

        // Not called again when getting the same element.
        assertEquals(16, lazyList[3])
        assertEquals(1, callCounter)
    }

    @Test
    @Suppress("TooGenericExceptionThrown")
    fun testMissingAttachments() {
        val lazyList = (0 until 5).toList().lazyMapped<Int, Int> { _, _ ->
            throw Exception("Uncatchable!")
        }

        assertThrows<Exception>("Uncatchable!") {
            lazyList.eagerDeserialise { _, _ -> -999 }
        }

    }

    @Test
    fun testDeserialisationExceptions() {
        val lazyList = (0 until 5).toList().lazyMapped<Int, Int> { _, _ ->
            throw IllegalStateException("Catch this!")
        }

        lazyList.eagerDeserialise { _, _ -> -999 }
        assertEquals(5, lazyList.size)
        lazyList.forEachIndexed { idx, item ->
            assertEquals(-999, item, "Item[$idx] mismatch")
        }
    }

    /**
     * Iterate over a [LazyMappedList], forcing it to transform all of its elements immediately.
     * This transformation is assumed to be "deserialisation". Does nothing for any other kind of [List].
     * WARNING: Any changes made to the [LazyMappedList] contents are PERMANENT!
     */
    private fun <T> List<T>.eagerDeserialise(onError: (RuntimeException, Int) -> T? = { ex, _ -> throw ex }) {
        if (this is LazyMappedList<*, T>) {
            eager(onError)
        }
    }
}
