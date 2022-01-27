package net.corda.membership.impl.read.reader

import net.corda.membership.impl.read.TestProperties.Companion.GROUP_ID_1
import net.corda.membership.impl.read.TestProperties.Companion.aliceName
import net.corda.membership.impl.read.TestProperties.Companion.bobName
import net.corda.membership.impl.read.cache.MemberDataCache
import net.corda.membership.impl.read.cache.MembershipGroupReadCache
import net.corda.membership.read.MembershipGroupReader
import net.corda.virtualnode.HoldingIdentity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MembershipGroupReaderFactoryTest {

    lateinit var membershipGroupReaderFactory: MembershipGroupReaderFactory

    val alice = aliceName
    val bob = bobName

    val aliceIdGroup1 = HoldingIdentity(alice.toString(), GROUP_ID_1)
    val bobIdGroup1 = HoldingIdentity(bob.toString(), GROUP_ID_1)

    val aliceReader: MembershipGroupReader = mock()
    val groupReaderCache = mock<MemberDataCache<MembershipGroupReader>>().apply {
        doReturn(aliceReader).whenever(this).get(eq(aliceIdGroup1))
        doReturn(null).whenever(this).get(eq(bobIdGroup1))
    }

    val cache: MembershipGroupReadCache = mock<MembershipGroupReadCache>().apply {
        doReturn(this@MembershipGroupReaderFactoryTest.groupReaderCache).whenever(this).groupReaderCache
    }

    @BeforeEach
    fun setUp() {
        membershipGroupReaderFactory = MembershipGroupReaderFactory.Impl(cache)
    }

    @Test
    fun `Cached readers are returned if available`() {
        val result = membershipGroupReaderFactory.getGroupReader(aliceIdGroup1)
        assertEquals(aliceReader, result)

        verify(groupReaderCache).get(eq(aliceIdGroup1))
        verify(groupReaderCache, never()).put(eq(aliceIdGroup1), eq(result))
    }

    @Test
    fun `New readers are created, cached and returned if no matching reader is cached already`() {
        val result = membershipGroupReaderFactory.getGroupReader(bobIdGroup1)
        assertNotEquals(aliceReader, result)

        verify(groupReaderCache).get(eq(bobIdGroup1))
        verify(groupReaderCache).put(eq(bobIdGroup1), eq(result))
    }
}