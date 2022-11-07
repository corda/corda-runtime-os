package net.corda.membership.impl.read.cache

import net.corda.membership.impl.read.TestProperties
import net.corda.membership.impl.read.TestProperties.Companion.GROUP_ID_1
import net.corda.membership.read.MembershipGroupReader
import net.corda.v5.membership.GroupParameters
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class MembershipGroupReadCacheTest {
    private lateinit var membershipGroupReadCache: MembershipGroupReadCache

    private val memberListCache get() = membershipGroupReadCache.memberListCache
    private val groupReaderCache get() = membershipGroupReadCache.groupReaderCache

    private val aliceName = TestProperties.aliceName
    private val aliceIdGroup1 = HoldingIdentity(aliceName, GROUP_ID_1)
    private val bob: MemberInfo = mock()
    private val membershipGroupReader: MembershipGroupReader = mock()
    private val groupParameters: GroupParameters = mock()

    @BeforeEach
    fun setUp() {
        membershipGroupReadCache = MembershipGroupReadCache.Impl()
    }


    @Test
    fun `Member list cache is accessible after initialising group cache`() {
        assertNotNull(memberListCache)
        memberListCache.put(aliceIdGroup1, bob)
        val lookup = memberListCache.get(aliceIdGroup1)
        assertNotNull(lookup)
        assertEquals(1, lookup?.size)
        assertEquals(bob, lookup?.first())
    }

    @Test
    fun `Group reader cache is accessible after starting initialising group cache`() {
        assertNotNull(groupReaderCache)
        groupReaderCache.put(aliceIdGroup1, membershipGroupReader)
        val lookup = groupReaderCache.get(aliceIdGroup1)
        assertNotNull(lookup)
        assertEquals(membershipGroupReader, lookup)
    }

    /*@Test
    fun `Group parameters cache is accessible after starting initialising group cache`() {
        assertNotNull(groupParamsCache)
        groupParamsCache.put(aliceIdGroup1, groupParameters)
        val lookup = groupParamsCache.get(aliceIdGroup1)
        assertNotNull(lookup)
        assertEquals(groupParameters, lookup)
    }*/

    @Test
    fun `Member list cache is cleared after clearing group cache`() {
        memberListCache.put(aliceIdGroup1, bob)
        membershipGroupReadCache.clear()
        assertNull(groupReaderCache.get(aliceIdGroup1))
    }

    @Test
    fun `Group reader cache is cleared after starting clearing group cache`() {
        groupReaderCache.put(aliceIdGroup1, membershipGroupReader)
        membershipGroupReadCache.clear()
        assertNull(groupReaderCache.get(aliceIdGroup1))
    }

    /*@Test
    fun `Group parameters cache is cleared after starting clearing group cache`() {
        groupParamsCache.put(aliceIdGroup1, groupParameters)
        membershipGroupReadCache.clear()
        assertNull(groupParamsCache.get(aliceIdGroup1))
    }*/
}