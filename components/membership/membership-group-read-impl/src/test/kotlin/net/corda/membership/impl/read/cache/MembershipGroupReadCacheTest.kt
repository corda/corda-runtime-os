package net.corda.membership.impl.read.cache

import net.corda.membership.impl.read.TestProperties
import net.corda.membership.impl.read.TestProperties.Companion.GROUP_ID_1
import net.corda.membership.read.MembershipGroupReader
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock

class MembershipGroupReadCacheTest {
    lateinit var membershipGroupReadCache: MembershipGroupReadCache

    val memberListCache get() = membershipGroupReadCache.memberListCache
    val groupReaderCache get() = membershipGroupReadCache.groupReaderCache

    val aliceName = TestProperties.aliceName
    val aliceIdGroup1 = HoldingIdentity(aliceName.toString(), GROUP_ID_1)
    val bob: MemberInfo = mock()
    val membershipGroupReader: MembershipGroupReader = mock()

    @BeforeEach
    fun setUp() {
        membershipGroupReadCache = MembershipGroupReadCache.Impl()
    }

    @Test
    fun `Cache is running after starting and not running after stopping`() {
        assertFalse(membershipGroupReadCache.isRunning)
        membershipGroupReadCache.start()
        assertTrue(membershipGroupReadCache.isRunning)
        membershipGroupReadCache.stop()
        assertFalse(membershipGroupReadCache.isRunning)
    }

    @Test
    fun `Member list cache is accessible after starting`() {
        membershipGroupReadCache.start()
        assertNotNull(memberListCache)
        memberListCache.put(aliceIdGroup1, bob)
        val lookup = memberListCache.get(aliceIdGroup1)
        assertNotNull(lookup)
        assertEquals(1, lookup?.size)
        assertEquals(bob, lookup?.first())
    }

    @Test
    fun `Group reader cache is accessible after starting`() {
        membershipGroupReadCache.start()
        assertNotNull(groupReaderCache)
        groupReaderCache.put(aliceIdGroup1, membershipGroupReader)
        val lookup = groupReaderCache.get(aliceIdGroup1)
        assertNotNull(lookup)
        assertEquals(membershipGroupReader, lookup)
    }

    @Test
    fun `Member list cache is not accessible before starting`() {
        val e = assertThrows<CordaRuntimeException> { memberListCache }
        assertEquals(
            String.format(
                MembershipGroupReadCache.Impl.UNINITIALIZED_CACHE_ERROR,
                MembershipGroupReadCache.Impl.MEMBER_LIST_CACHE
            ), e.message
        )
    }

    @Test
    fun `Group reader cache is not accessible before starting`() {
        val e = assertThrows<CordaRuntimeException> { groupReaderCache }
        assertEquals(
            String.format(
                MembershipGroupReadCache.Impl.UNINITIALIZED_CACHE_ERROR,
                MembershipGroupReadCache.Impl.GROUP_READER_CACHE
            ), e.message
        )
    }

    @Test
    fun `Member list cache is not accessible after stopping`() {
        membershipGroupReadCache.start()
        membershipGroupReadCache.stop()
        assertThrows<CordaRuntimeException> { memberListCache }
    }

    @Test
    fun `Group reader cache is not accessible after stopping`() {
        membershipGroupReadCache.start()
        membershipGroupReadCache.stop()
        assertThrows<CordaRuntimeException> { groupReaderCache }
    }
}