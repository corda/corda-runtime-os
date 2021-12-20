package net.corda.membership.impl.read.cache

import net.corda.membership.read.MembershipGroupReader
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.membership.identity.MemberInfo
import net.corda.v5.membership.identity.MemberX500Name
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

    val groupId = "ABC123"
    val aliceName = MemberX500Name("Alice", "London", "GB")
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
        memberListCache.put(groupId, aliceName, bob)
        val lookup = memberListCache.get(groupId, aliceName)
        assertNotNull(lookup)
        assertEquals(1, lookup?.size)
        assertEquals(bob, lookup?.first())
    }

    @Test
    fun `Group reader cache is accessible after starting`() {
        membershipGroupReadCache.start()
        assertNotNull(groupReaderCache)
        groupReaderCache.put(groupId, aliceName, membershipGroupReader)
        val lookup = groupReaderCache.get(groupId, aliceName)
        assertNotNull(lookup)
        assertEquals(membershipGroupReader, lookup)
    }

    @Test
    fun `Member list cache is not accessible before starting`() {
        assertThrows<CordaRuntimeException> { memberListCache }
    }

    @Test
    fun `Group reader cache is not accessible before starting`() {
        assertThrows<CordaRuntimeException> { groupReaderCache }
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