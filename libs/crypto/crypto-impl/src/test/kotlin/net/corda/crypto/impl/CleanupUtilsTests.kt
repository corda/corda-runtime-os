package net.corda.crypto.impl

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever
import kotlin.test.assertTrue

class CleanupUtilsTests {
    @Test
    @Timeout(5)
    fun `Should close gracefully`() {
        val autoCloseable = mock<AutoCloseable>()
        autoCloseable.closeGracefully()
        Mockito.verify(autoCloseable, times(1)).close()
    }

    @Test
    @Timeout(5)
    fun `Should ignore exception when closing gracefully`() {
        val autoCloseable = mock<AutoCloseable>()
        whenever(
            autoCloseable.close()
        ).thenThrow(RuntimeException())
        autoCloseable.closeGracefully()
        Mockito.verify(autoCloseable, times(1)).close()
    }

    @Test
    @Timeout(5)
    fun `Should clear mutable map gracefully`() {
        val map = mutableMapOf<Any, Any?>(
            "k0" to mock<AutoCloseable>(),
            "k1" to mock<AutoCloseable>(),
            "k2" to Any(),
            "k3" to null,
            "k4" to mock<AutoCloseable>()
        )
        whenever(
            (map["k0"] as AutoCloseable).close()
        ).thenThrow(RuntimeException())
        val clone = map.toMap()
        map.clearCache()
        assertTrue(map.isEmpty())
        Mockito.verify(clone["k1"] as AutoCloseable, times(1)).close()
        Mockito.verify(clone["k4"] as AutoCloseable, times(1)).close()
    }
}