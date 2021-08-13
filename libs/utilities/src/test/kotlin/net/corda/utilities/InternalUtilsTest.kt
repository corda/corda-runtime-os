package net.corda.utilities

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

open class InternalUtilsTest {

    @Test
	fun `indexOfOrThrow returns index of the given item`() {
        val collection = listOf(1, 2)
        assertEquals(collection.indexOfOrThrow(1), 0)
        assertEquals(collection.indexOfOrThrow(2), 1)
    }

    @Test
	fun `indexOfOrThrow throws if the given item is not found`() {
        val collection = listOf(1)
        assertFailsWith<IllegalArgumentException> { collection.indexOfOrThrow(2) }
    }

    @Test
	fun kotlinObjectInstance() {
        assertThat(PublicObject::class.java.kotlinObjectInstance).isSameAs(PublicObject)
        assertThat(PrivateObject::class.java.kotlinObjectInstance).isSameAs(PrivateObject)
        assertThat(ProtectedObject::class.java.kotlinObjectInstance).isSameAs(ProtectedObject)
        assertThat(PrivateClass::class.java.kotlinObjectInstance).isNull()
    }

    @Test
	fun `warnOnce works, but the backing cache grows only to a maximum size`() {
        val maxSize = 100

        val logger = mock<Logger>()
        logger.warnOnce("a")
        logger.warnOnce("b")
        logger.warnOnce("b")

        // This should cause the eviction of "a".
        (1..maxSize).forEach { logger.warnOnce("$it") }
        logger.warnOnce("a")

        // "a" should be logged twice because it was evicted.
        verify(logger, times(2)).warn("a")

        // "b" should be logged only once because there was no eviction.
        verify(logger, times(1)).warn("b")
    }

    object PublicObject
    private object PrivateObject
    protected object ProtectedObject

    private class PrivateClass
}
