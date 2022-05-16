package net.corda.entityprocessor.impl.tests.helpers

import org.mockito.Mockito

// Might not be needed - Mockito.any() may just work.
object MockitoHelper {
    fun <T> anyObject(): T {
        Mockito.any<T>()
        return uninitialized()
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> uninitialized(): T = null as T
}
