package net.corda.flow.fiber

import net.corda.virtualnode.HoldingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.util.UUID

class FlowFiberCacheImplTest {

    private val cache = FlowFiberCacheImpl()
    private val holdingIdentity = mock<HoldingIdentity>()

    data class MockCacheItem(private val value: String)

    @Test
    fun `can put into the cache, and retrieve the value`() {
        val rand = UUID.randomUUID().toString()
        val mockFiber = MockCacheItem("fiber_$rand")
        val key = FlowFiberCacheKey(holdingIdentity, "flow_$rand")

        cache.put(key, mockFiber)

        val retrieved = cache.get(key)
        assertThat(retrieved).isEqualTo(mockFiber)
    }

    @Test
    fun `invalidating an entry in the cache will remove it from the cache`() {
        val rand = UUID.randomUUID().toString()
        val mockFiber = MockCacheItem("fiber_$rand")
        val key = FlowFiberCacheKey(holdingIdentity, "flow_$rand")

        cache.put(key, mockFiber)

        val retrieved = cache.get(key)
        assertThat(retrieved).isEqualTo(mockFiber)

        cache.remove(key)

        assertThat(cache.get(key)).isNull()
    }

    @Test
    fun `retrieving from cache a key that doesn't exist returns null`() {
        val rand = UUID.randomUUID().toString()
        val key = FlowFiberCacheKey(holdingIdentity, "flow_$rand")

        assertThat(cache.get(key)).isNull()
    }
}