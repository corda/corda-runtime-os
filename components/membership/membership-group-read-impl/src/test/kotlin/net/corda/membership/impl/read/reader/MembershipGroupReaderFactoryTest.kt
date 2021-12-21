package net.corda.membership.impl.read.reader

import net.corda.membership.impl.read.TestProperties.Companion.GROUP_ID_1
import net.corda.membership.impl.read.TestProperties.Companion.aliceName
import net.corda.membership.impl.read.TestProperties.Companion.bobName
import net.corda.membership.impl.read.cache.MemberDataCache
import net.corda.membership.impl.read.cache.MembershipGroupReadCache
import net.corda.membership.read.MembershipGroupReader
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

    val aliceReader: MembershipGroupReader = mock()
    val groupReaderCache = mock<MemberDataCache<MembershipGroupReader>>().apply {
        doReturn(aliceReader).whenever(this).get(eq(GROUP_ID_1), eq(alice))
        doReturn(null).whenever(this).get(eq(GROUP_ID_1), eq(bob))
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
        val result = membershipGroupReaderFactory.getGroupReader(GROUP_ID_1, alice)
        assertEquals(aliceReader, result)

        verify(groupReaderCache).get(eq(GROUP_ID_1), eq(alice))
        verify(groupReaderCache, never()).put(eq(GROUP_ID_1), eq(alice), eq(result))
    }

    @Test
    fun `New readers are created, cached and returned if no matching reader is cached already`() {
        val result = membershipGroupReaderFactory.getGroupReader(GROUP_ID_1, bob)
        assertNotEquals(aliceReader, result)

        verify(groupReaderCache).get(eq(GROUP_ID_1), eq(bob))
        verify(groupReaderCache).put(eq(GROUP_ID_1), eq(bob), eq(result))
    }
}