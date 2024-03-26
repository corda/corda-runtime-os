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
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FlowFibreCacheTest {
    private val cacheEviction = mock<CacheEviction>()
    private val key1 = mock<FlowKey>()
    private val key2 = mock<FlowKey>()
    private val value1 = mock<FlowFiber>()
    private val value2 = mock<FlowFiber>()
    private val sandboxGroupId1 = UUID.randomUUID()
    private val sandboxGroupId2 = UUID.randomUUID()
    private val cache = FlowFiberCacheImpl(cacheEviction)

    @BeforeAll
    fun setup() {
        whenever(value1.getSandboxGroupId()).thenReturn(sandboxGroupId1)
        whenever(value2.getSandboxGroupId()).thenReturn(sandboxGroupId2)
    }
    @Test
    fun `when get and no entry return null`() {
        val entry = cache.get(mock(), 123, sandboxGroupId1)
        assertThat(entry).isNull()
    }

    @Test
    fun `when get and entry wrong version return null and entry evicted`() {
        cache.put(key1, 1, value1)
        val entry = cache.get(key1, 123, sandboxGroupId1)
        assertThat(entry).isNull()
        assertThat(cache.get(key1, 1, sandboxGroupId1)).isNull()
    }

    @Test
    fun `when get and entry wrong sandbox group ID return null and entry evicted`() {
        cache.put(key1, 1, value1)
        val entry = cache.get(key1, 1, UUID.randomUUID())
        assertThat(entry).isNull()
        assertThat(cache.get(key1, 1, sandboxGroupId1)).isNull()
    }

    @Test
    fun `interrupted thread prevented from writing to cache`() {
        Thread.currentThread().interrupt()

        assertThrows<InterruptedException> {
            cache.put(key1, 1, value1)
        }
    }

    @Test
    fun `interrupted thread prevented from getting from cache`() {
        Thread.currentThread().interrupt()

        assertThrows<InterruptedException> {
            cache.get(key1, 1, UUID.randomUUID())
        }
    }

    @Test
    fun `interrupted thread prevented from removing from cache`() {
        Thread.currentThread().interrupt()

        assertThrows<InterruptedException> {
            cache.remove(key1)
        }
    }

    @Test
    fun `when get and entry and version exist and matches sandbox group ID return`() {
        cache.put(key1, 1, value1)
        val entry = cache.get(key1, 1, sandboxGroupId1)
        assertThat(entry).isSameAs(value1)
    }

    @Test
    fun `get from cache also removes entry`() {
        cache.put(key1, 1, value1)
        val firstRetrieval = cache.get(key1, 1, sandboxGroupId1)
        val secondRetrieval = cache.get(key1, 1, sandboxGroupId1)

        assertThat(firstRetrieval).isEqualTo(value1)
        assertThat(secondRetrieval).isNull()
    }

    @Test
    fun `when remove and entry does not exist do no throw`() {
        assertDoesNotThrow {
            cache.remove(key1)
        }
    }

    @Test
    fun `removeAll when data exists`() {
        val key1 = mock<FlowKey>()
        val key2 = mock<FlowKey>()
        whenever(value1.getSandboxGroupId()).thenReturn(sandboxGroupId1)
        whenever(value2.getSandboxGroupId()).thenReturn(sandboxGroupId2)

        cache.put(key1, 1, value1)
        cache.put(key2, 3, value2)
        cache.removeAll()
        assertThat(cache.get(this.key1, 1, sandboxGroupId1)).isNull()
        assertThat(cache.get(this.key2, 3, sandboxGroupId2)).isNull()
    }

    @Test
    fun `removeAll does not throw when no data exists`() {
        assertDoesNotThrow {
            cache.removeAll()
        }
    }

    @Test
    fun `removeAll clears cache when data exists`() {
        cache.put(key1, 1, value1)
        cache.put(key2, 1, value2)
        assertThat(cache.get(key1, 1, sandboxGroupId1)).isNotNull()
        assertThat(cache.get(key2, 1, sandboxGroupId2)).isNotNull()
        cache.removeAll()
        assertThat(cache.get(key1, 1, sandboxGroupId1)).isNull()
        assertThat(cache.get(key2, 1, sandboxGroupId2)).isNull()
    }

    @Test
    fun `when remove by vnode context remove all`() {
        val id = mock<HoldingIdentity> {
            on { x500Name } doReturn (MemberX500Name("Bruce", "Thomas", "GB"))
            on { groupId } doReturn ("Batman")
        }
        val avroId = id.toAvro()
        val vnodeContext = mock< VirtualNodeContext> {
            on { holdingIdentity } doReturn (id)
        }
        val key1 = mock<FlowKey> {
            on { identity } doReturn (avroId)
        }
        val key2 = mock<FlowKey> {
            on { identity } doReturn (avroId)
        }
        cache.put(key1, 1, mock())
        cache.put(key2, 1, mock())
        cache.remove(vnodeContext)
        assertThat(cache.get(key1, 1, sandboxGroupId1)).isNull()
        assertThat(cache.get(key2, 1, sandboxGroupId1)).isNull()
    }
}