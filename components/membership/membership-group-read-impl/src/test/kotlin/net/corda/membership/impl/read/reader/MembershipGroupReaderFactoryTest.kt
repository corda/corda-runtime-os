package net.corda.membership.impl.read.reader

import net.corda.libs.platform.PlatformInfoProvider
import net.corda.membership.impl.read.TestProperties.Companion.GROUP_ID_1
import net.corda.membership.impl.read.TestProperties.Companion.aliceName
import net.corda.membership.impl.read.TestProperties.Companion.bobName
import net.corda.membership.impl.read.cache.MemberDataCache
import net.corda.membership.impl.read.cache.MembershipGroupReadCache
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.read.GroupParametersReaderService
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

    private lateinit var membershipGroupReaderFactory: MembershipGroupReaderFactory

    private val alice = aliceName
    private val bob = bobName

    private val aliceIdGroup1 = HoldingIdentity(alice, GROUP_ID_1)
    private val bobIdGroup1 = HoldingIdentity(bob, GROUP_ID_1)

    private val aliceReader: MembershipGroupReader = mock()
    private val groupReaderCache = mock<MemberDataCache<MembershipGroupReader>>().apply {
        doReturn(aliceReader).whenever(this).get(eq(aliceIdGroup1))
        doReturn(null).whenever(this).get(eq(bobIdGroup1))
    }

    private val cache: MembershipGroupReadCache = mock<MembershipGroupReadCache>().apply {
        doReturn(this@MembershipGroupReaderFactoryTest.groupReaderCache).whenever(this).groupReaderCache
    }

    private val groupParametersReaderService: GroupParametersReaderService = mock()
    private val memberInfoFactory: MemberInfoFactory = mock()
    private val platformInfoProvider: PlatformInfoProvider = mock()

    @BeforeEach
    fun setUp() {
        membershipGroupReaderFactory = MembershipGroupReaderFactory.Impl(
            cache, groupParametersReaderService, memberInfoFactory, platformInfoProvider
        )
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