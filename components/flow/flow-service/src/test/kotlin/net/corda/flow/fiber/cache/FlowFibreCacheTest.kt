package net.corda.flow.fiber.cache

import net.corda.data.flow.FlowKey
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.cache.impl.FlowFiberCacheImpl
import net.corda.sandboxgroupcontext.VirtualNodeContext
import net.corda.sandboxgroupcontext.service.CacheEviction
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class FlowFibreCacheTest {
    private val cacheEviction = mock< CacheEviction>()
    private val key = mock<FlowKey>()
    private val value = mock<FlowFiber>()

    @Test
    fun `when get and no entry return null`() {
        val cache = FlowFiberCacheImpl(cacheEviction)
        val entry = cache.get(mock(), 123)
        assertThat(entry).isNull()
    }

    @Test
    fun `when get and entry wrong version return null`() {
        val cache = FlowFiberCacheImpl(cacheEviction)
        cache.put(key, 1, value)
        val entry = cache.get(mock(), 123)
        assertThat(entry).isNull()
    }

    @Test
    fun `when get and entry and version exist return`() {
        val cache = FlowFiberCacheImpl(cacheEviction)
        cache.put(key, 1, value)
        val entry = cache.get(key, 1)
        assertThat(entry).isSameAs(value)
    }

    @Test
    fun `when remove and entry does not exist do no throw`() {
        val cache = FlowFiberCacheImpl(cacheEviction)
        assertDoesNotThrow {
            cache.remove(key, 1)
        }
    }

    @Test
    fun `when remove and entry does not exist at suspendCount do no throw`() {
        val cache = FlowFiberCacheImpl(cacheEviction)
        cache.put(key, 1, value)
        assertDoesNotThrow {
            cache.remove(key, 123)
        }
        assertThat(cache.get(key, 1)).isSameAs(value)
    }

    @Test
    fun `when remove and exists`() {
        val cache = FlowFiberCacheImpl(cacheEviction)
        cache.put(key, 1, value)
        cache.remove(key, 1)
        assertThat(cache.get(key, 1)).isNull()
    }

    @Test
    fun `when remove by vnode context remover all`() {
        val id = mock<HoldingIdentity> {
            on { x500Name } doReturn (MemberX500Name("Bruce", "Thomas", "GB"))
            on { groupId } doReturn ("Batman")
        }
        val avroId = id.toAvro()
        val vnodeContext = mock< VirtualNodeContext> {
            on { holdingIdentity } doReturn (id)
        }
        val cache = FlowFiberCacheImpl(cacheEviction)
        val key1 = mock<FlowKey> {
            on { identity } doReturn (avroId)
        }
        val key2 = mock<FlowKey> {
            on { identity } doReturn (avroId)
        }
        cache.put(key1, 1, mock())
        cache.put(key2, 1, mock())
        cache.remove(vnodeContext)
        assertThat(cache.get(key1, 1)).isNull()
        assertThat(cache.get(key2, 1)).isNull()
    }
}