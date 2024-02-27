package net.corda.membership.impl.read.cache

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.Meter
import net.corda.membership.impl.read.TestProperties.Companion.GROUP_ID_1
import net.corda.membership.impl.read.TestProperties.Companion.GROUP_ID_2
import net.corda.membership.impl.read.TestProperties.Companion.aliceName
import net.corda.membership.impl.read.TestProperties.Companion.bobName
import net.corda.membership.impl.read.TestProperties.Companion.charlieName
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_PENDING
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.metrics.CordaMetrics
import net.corda.test.util.metrics.CORDA_METRICS_LOCK
import net.corda.test.util.metrics.EachTestCordaMetrics
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Condition
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import kotlin.math.roundToLong
import io.micrometer.core.instrument.Tag as micrometerTag

@ResourceLock(CORDA_METRICS_LOCK)
class MemberListCacheImplTest {
    private lateinit var memberListCache: MemberListCache

    @Suppress("unused")
    @RegisterExtension
    private val metrics = EachTestCordaMetrics("MemberList Testing")

    private val alice = aliceName
    private val bob = bobName
    private val charlie = charlieName

    private val aliceIdGroup1 = HoldingIdentity(alice, GROUP_ID_1)
    private val bobIdGroup1 = HoldingIdentity(bob, GROUP_ID_1)
    private val aliceIdGroup2 = HoldingIdentity(alice, GROUP_ID_2)

    private val pendingContext = mock<MGMContext> {
        on { parse(STATUS, String::class.java) } doReturn MEMBER_STATUS_PENDING
    }
    private val activeContext = mock<MGMContext> {
        on { parse(STATUS, String::class.java) } doReturn MEMBER_STATUS_ACTIVE
    }
    private val suspendedContext = mock<MGMContext> {
        on { parse(STATUS, String::class.java) } doReturn MEMBER_STATUS_SUSPENDED
    }

    private val bobInfo = mock<MemberInfo> {
        on { mgmProvidedContext } doReturn activeContext
        on { name } doReturn bob
    }
    private val aliceInfo = mock<MemberInfo> {
        on { mgmProvidedContext } doReturn activeContext
        on { name } doReturn alice
    }

    @Suppress("SameParameterValue")
    private fun nameOf(expected: String): Condition<in Meter> {
        return Condition<Meter>({ m -> m.id.name == expected }, "name of %s", expected)
    }

    private fun tagOf(tag: CordaMetrics.Tag, tagValue: String): Condition<in Meter> {
        val expectedTag = micrometerTag.of(tag.value, tagValue)
        return Condition<Meter>({ m -> m.id.tags.contains(expectedTag) }, "tag of %s", expectedTag)
    }

    private fun singleValueOf(expected: Long): Condition<in Meter> {
        return Condition<Meter>({ m -> m.measure().single().value.roundToLong() == expected }, "value of %d", expected)
    }

    @BeforeEach
    fun setUp() {
        memberListCache = MemberListCache.Impl()
    }

    @Test
    fun `Get member list before any data is cached`() {
        assertThat(lookupWithDefaults()).isEmpty()
    }

    @Test
    fun `Add member list to cache then read same member from cache`() {
        memberListCache.put(aliceIdGroup1, listOf(aliceInfo, bobInfo))
        assertThat(lookupWithDefaults()).containsExactlyInAnyOrder(aliceInfo, bobInfo)
        assertThat(CordaMetrics.registry.meters)
            .hasSize(1)
            .element(0).isInstanceOf(Gauge::class.java)
            .has(nameOf("corda.membership.memberlist.cache.size"))
            .has(tagOf(CordaMetrics.Tag.MembershipGroup, aliceIdGroup1.groupId))
            .has(tagOf(CordaMetrics.Tag.VirtualNode, aliceIdGroup1.shortHash.value))
            .has(singleValueOf(2))
    }

    @Test
    fun `Add single member to cache then read same member from cache`() {
        addToCacheWithDefaults()
        assertThat(lookupWithDefaults()).containsExactly(bobInfo)
        assertThat(CordaMetrics.registry.meters)
            .hasSize(1)
            .element(0).isInstanceOf(Gauge::class.java)
            .has(nameOf("corda.membership.memberlist.cache.size"))
            .has(tagOf(CordaMetrics.Tag.MembershipGroup, aliceIdGroup1.groupId))
            .has(tagOf(CordaMetrics.Tag.VirtualNode, aliceIdGroup1.shortHash.value))
            .has(singleValueOf(1))
    }

    @Test
    fun `Cache for one member and lookup for a different member`() {
        addToCacheWithDefaults()
        assertThat(lookupWithDefaults(bobIdGroup1)).isEmpty()
    }

    @Test
    fun `Cache for one group and lookup for a different empty group`() {
        addToCacheWithDefaults()
        assertThat(lookupWithDefaults(aliceIdGroup2)).isEmpty()
    }

    @Test
    fun `Cache and lookup for a multiple groups`() {
        addToCacheWithDefaults()
        addToCacheWithDefaults(aliceIdGroup2, memberInfo = aliceInfo)

        assertThat(lookupWithDefaults()).containsExactly(bobInfo)
        assertThat(lookupWithDefaults(aliceIdGroup2)).containsExactly(aliceInfo)
    }

    @Test
    fun `get all returns all information from cache`() {
        addToCacheWithDefaults()
        addToCacheWithDefaults(aliceIdGroup2, memberInfo = aliceInfo)

        val cache = memberListCache.getAll()
        assertThat(cache).hasSize(2)
        assertThat(cache[aliceIdGroup1]).containsExactly(bobInfo)
        assertThat(cache[aliceIdGroup2]).containsExactly(aliceInfo)
    }

    @Test
    fun `Cache and lookup for multiple members in the same group`() {
        addToCacheWithDefaults()
        addToCacheWithDefaults(bobIdGroup1, memberInfo = aliceInfo)

        assertThat(lookupWithDefaults()).containsExactly(bobInfo)
        assertThat(lookupWithDefaults(bobIdGroup1)).containsExactly(aliceInfo)
    }

    @Test
    fun `Cache output is immutable`() {
        addToCacheWithDefaults()
        val lookupResult = lookupWithDefaults()
        assertThat(lookupResult).isNotEmpty
        assertThrows<ClassCastException> {
            lookupResult as MutableList<MemberInfo>
        }
        assertThrows<UnsupportedOperationException> {
            java.util.List::class.java.cast(lookupResult).clear()
        }
    }

    @Test
    fun `Member info discarded if status is the same - active`() {
        val originalInfo = mock<MemberInfo> {
            on { mgmProvidedContext } doReturn activeContext
            on { name } doReturn charlie
        }
        val updatedInfo = mock<MemberInfo> {
            on { mgmProvidedContext } doReturn activeContext
            on { name } doReturn charlie
        }

        addToCacheWithDefaults(memberInfo = originalInfo)
        addToCacheWithDefaults(memberInfo = updatedInfo)
        assertThat(lookupWithDefaults()).containsExactly(updatedInfo)
    }

    @Test
    fun `Member info discarded if status is the same - suspended`() {
        val originalInfo = mock<MemberInfo> {
            on { mgmProvidedContext } doReturn suspendedContext
            on { name } doReturn charlie
        }
        val updatedInfo = mock<MemberInfo> {
            on { mgmProvidedContext } doReturn suspendedContext
            on { name } doReturn charlie
        }

        addToCacheWithDefaults(memberInfo = originalInfo)
        addToCacheWithDefaults(memberInfo = updatedInfo)
        assertThat(lookupWithDefaults()).containsExactly(updatedInfo)
    }

    @Test
    fun `Member info discarded if status is the same - pending`() {
        val originalInfo = mock<MemberInfo> {
            on { mgmProvidedContext } doReturn pendingContext
            on { name } doReturn charlie
        }
        val updatedInfo = mock<MemberInfo> {
            on { mgmProvidedContext } doReturn pendingContext
            on { name } doReturn charlie
        }

        addToCacheWithDefaults(memberInfo = originalInfo)
        addToCacheWithDefaults(memberInfo = updatedInfo)
        assertThat(lookupWithDefaults()).containsExactly(updatedInfo)
    }

    @Test
    fun `Member info discarded if status is active or suspended`() {
        val activeInfo = mock<MemberInfo> {
            on { mgmProvidedContext } doReturn activeContext
            on { name } doReturn charlie
        }
        val suspendedInfo = mock<MemberInfo> {
            on { mgmProvidedContext } doReturn suspendedContext
            on { name } doReturn charlie
        }

        addToCacheWithDefaults(memberInfo = activeInfo)
        // suspend member
        addToCacheWithDefaults(memberInfo = suspendedInfo)
        assertThat(lookupWithDefaults()).containsExactly(suspendedInfo)
        // activate member
        addToCacheWithDefaults(memberInfo = activeInfo)
        assertThat(lookupWithDefaults()).containsExactly(activeInfo)
    }

    @Test
    fun `Member info not discarded if status is pending and then active`() {
        val originalInfo = mock<MemberInfo> {
            on { mgmProvidedContext } doReturn pendingContext
            on { name } doReturn charlie
        }
        val updatedInfo = mock<MemberInfo> {
            on { mgmProvidedContext } doReturn activeContext
            on { name } doReturn charlie
        }

        addToCacheWithDefaults(memberInfo = originalInfo)
        addToCacheWithDefaults(memberInfo = updatedInfo)
        // add extra member
        addToCacheWithDefaults(memberInfo = bobInfo)
        assertThat(lookupWithDefaults()).containsExactlyInAnyOrder(bobInfo, originalInfo, updatedInfo)
    }

    @Test
    fun `clear empties the cache`() {
        memberListCache.put(aliceIdGroup1, listOf(bobInfo))
        assertThat(lookupWithDefaults()).containsExactly(bobInfo)
        assertThat(CordaMetrics.registry.meters).hasSize(1)

        memberListCache.clear()
        assertThat(lookupWithDefaults()).isEmpty()
        assertThat(CordaMetrics.registry.meters).isEmpty()
    }

    private fun lookupWithDefaults(
        holdingIdentity: HoldingIdentity = aliceIdGroup1
    ): List<MemberInfo>? {
        return memberListCache.get(holdingIdentity)
    }

    private fun addToCacheWithDefaults(
        holdingIdentity: HoldingIdentity = aliceIdGroup1,
        memberInfo: MemberInfo = bobInfo
    ) {
        memberListCache.put(holdingIdentity, memberInfo)
    }
}
