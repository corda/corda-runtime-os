package net.corda.db.persistence.testkit.helpers

import org.mockito.Mockito

/** Helps where `Mockito.any()` can't infer the type correctly and returns `null` instead */
object MockitoHelper {
    fun <T> anyObject(): T {
        Mockito.any<T>()
        return uninitialized()
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> uninitialized(): T = null as T
}
