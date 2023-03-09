package net.corda.membership.impl.read.reader

import net.corda.crypto.cipher.suite.PublicKeyHash
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.crypto.cipher.suite.sha256Bytes
import net.corda.membership.impl.read.TestProperties
import net.corda.membership.impl.read.TestProperties.Companion.GROUP_ID_1
import net.corda.membership.impl.read.cache.MemberListCache
import net.corda.membership.impl.read.cache.MembershipGroupReadCache
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEY_HASHES
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_PENDING
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED
import net.corda.membership.lib.MemberInfoExtension.Companion.SESSION_KEY_HASH
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.SignedGroupParameters
import net.corda.membership.read.GroupParametersReaderService
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.security.PublicKey

class MembershipGroupReaderImplTest {
    private lateinit var membershipGroupReaderImpl: MembershipGroupReaderImpl

    private val aliceName = TestProperties.aliceName
    private val aliceIdGroup1 = HoldingIdentity(aliceName, GROUP_ID_1)
    private val bobName = TestProperties.bobName
    private val memberCache: MemberListCache = mock()
    private val membershipGroupCache: MembershipGroupReadCache = mock<MembershipGroupReadCache>().apply {
        whenever(this.memberListCache).thenReturn(memberCache)
    }
    private val groupParameters: SignedGroupParameters = mock()
    private val groupParametersReaderService: GroupParametersReaderService = mock {
        on { get(eq(aliceIdGroup1)) } doReturn groupParameters
    }
    private val mockLedgerKey: PublicKey = mock()
    private val mockLedgerKeyAsByteArray = "1234".toByteArray()
    private val mockLedgerKeyHash = PublicKeyHash.parse(mockLedgerKeyAsByteArray.sha256Bytes())
    private val mockSessionKey: PublicKey = mock()
    private val mockSessionKeyAsByteArray = "5678".toByteArray()
    private val mockSessionKeyHash = PublicKeyHash.parse(mockSessionKeyAsByteArray.sha256Bytes())
    private val mockedSuspendedMemberProvidedContext = mock<MemberContext> {
        on { parseSet(eq(LEDGER_KEY_HASHES), eq(PublicKeyHash::class.java)) } doReturn setOf(mockLedgerKeyHash)
        on { parseOrNull(eq(SESSION_KEY_HASH), eq(PublicKeyHash::class.java)) } doReturn mockSessionKeyHash
    }
    private val mockedSuspendedMgmProvidedContext = mock<MGMContext> {
        on { parse(eq(STATUS), eq(String::class.java)) } doReturn MEMBER_STATUS_SUSPENDED
    }
    private val aliceSuspendedMemberInfo: MemberInfo = mock {
        on { name } doReturn aliceName
        on { ledgerKeys } doReturn listOf(mockLedgerKey)
        on { sessionInitiationKey } doReturn mockSessionKey
        on { memberProvidedContext } doReturn mockedSuspendedMemberProvidedContext
        on { mgmProvidedContext } doReturn mockedSuspendedMgmProvidedContext
        on { isActive } doReturn false
    }
    private val bobSuspendedMemberInfo: MemberInfo = mock {
        on { name } doReturn bobName
        on { ledgerKeys } doReturn listOf(mockLedgerKey)
        on { sessionInitiationKey } doReturn mockSessionKey
        on { memberProvidedContext } doReturn mockedSuspendedMemberProvidedContext
        on { mgmProvidedContext } doReturn mockedSuspendedMgmProvidedContext
        on { isActive } doReturn false
    }

    private val mockedActiveMemberProvidedContext = mock<MemberContext> {
        on { parseSet(eq(LEDGER_KEY_HASHES), eq(PublicKeyHash::class.java)) } doReturn setOf(mockLedgerKeyHash)
        on { parseOrNull(eq(SESSION_KEY_HASH), eq(PublicKeyHash::class.java)) } doReturn mockSessionKeyHash
        on { parse(eq(STATUS), eq(String::class.java)) } doReturn MEMBER_STATUS_ACTIVE
    }
    private val mockedActiveMgmProvidedContext = mock<MGMContext> {
        on { parse(eq(STATUS), eq(String::class.java)) } doReturn MEMBER_STATUS_ACTIVE
    }
    private val aliceActiveMemberInfo: MemberInfo = mock {
        on { name } doReturn aliceName
        on { ledgerKeys } doReturn listOf(mockLedgerKey)
        on { sessionInitiationKey } doReturn mockSessionKey
        on { memberProvidedContext } doReturn mockedActiveMemberProvidedContext
        on { mgmProvidedContext } doReturn mockedActiveMgmProvidedContext
        on { isActive } doReturn true
    }
    private val bobActiveMemberInfo: MemberInfo = mock {
        on { name } doReturn bobName
        on { ledgerKeys } doReturn listOf(mockLedgerKey)
        on { sessionInitiationKey } doReturn mockSessionKey
        on { memberProvidedContext } doReturn mockedActiveMemberProvidedContext
        on { mgmProvidedContext } doReturn mockedActiveMgmProvidedContext
        on { isActive } doReturn true
    }

    private val mockedPendingMgmProvidedContext = mock<MGMContext> {
        on { parse(eq(STATUS), eq(String::class.java)) } doReturn MEMBER_STATUS_PENDING
    }
    private val alicePendingMemberInfo: MemberInfo = mock {
        on { name } doReturn aliceName
        on { memberProvidedContext } doReturn mockedActiveMemberProvidedContext
        on { mgmProvidedContext } doReturn mockedPendingMgmProvidedContext
        on { isActive } doReturn false
    }
    private val bobPendingMemberInfo: MemberInfo = mock {
        on { name } doReturn bobName
        on { memberProvidedContext } doReturn mockedActiveMemberProvidedContext
        on { mgmProvidedContext } doReturn mockedPendingMgmProvidedContext
        on { isActive } doReturn false
    }

    @BeforeEach
    fun setUp() {
        membershipGroupReaderImpl = MembershipGroupReaderImpl(
            aliceIdGroup1,
            membershipGroupCache,
            groupParametersReaderService
        )
    }

    private fun mockMemberList(memberList: List<MemberInfo>) {
        whenever(memberCache.get(eq(aliceIdGroup1))).thenReturn(memberList)
    }

    @Test
    fun `lookup known member with active status based on name`() {
        mockMemberList(listOf(aliceActiveMemberInfo, alicePendingMemberInfo))
        assertEquals(aliceActiveMemberInfo, membershipGroupReaderImpl.lookup(aliceName))
    }

    @Test
    fun `lookup known member with falling back on pending status if no active state is present based on name`() {
        mockMemberList(listOf(alicePendingMemberInfo, aliceSuspendedMemberInfo, bobPendingMemberInfo))
        assertEquals(
            alicePendingMemberInfo,
            membershipGroupReaderImpl.lookup(aliceName, MembershipStatusFilter.ACTIVE_IF_PRESENT_OR_PENDING)
        )
        mockMemberList(listOf(aliceActiveMemberInfo, alicePendingMemberInfo, bobActiveMemberInfo))
        assertEquals(
            aliceActiveMemberInfo,
            membershipGroupReaderImpl.lookup(aliceName, MembershipStatusFilter.ACTIVE_IF_PRESENT_OR_PENDING)
        )
    }

    @Test
    fun `lookup known member with suspended status based on name`() {
        mockMemberList(listOf(aliceSuspendedMemberInfo, alicePendingMemberInfo))
        assertEquals(
            aliceSuspendedMemberInfo,
            membershipGroupReaderImpl.lookup(aliceName, MembershipStatusFilter.ACTIVE_OR_SUSPENDED)
        )
    }

    @Test
    fun `lookup known member with pending status based on name`() {
        mockMemberList(listOf(alicePendingMemberInfo, aliceActiveMemberInfo))
        assertEquals(alicePendingMemberInfo, membershipGroupReaderImpl.lookup(aliceName, MembershipStatusFilter.PENDING))
    }

    @Test
    fun `lookup known member with pending status based on name with active filter`() {
        mockMemberList(listOf(alicePendingMemberInfo))
        assertNull(membershipGroupReaderImpl.lookup(aliceName))
    }


    @Test
    fun `lookup non-existing member based on name`() {
        mockMemberList(emptyList())
        assertNull(membershipGroupReaderImpl.lookup(aliceName))
    }

    @Test
    fun `lookup known member with active status based on ledger public key hash`() {
        mockMemberList(listOf(aliceActiveMemberInfo, alicePendingMemberInfo))
        assertEquals(aliceActiveMemberInfo, membershipGroupReaderImpl.lookupByLedgerKey(mockLedgerKeyHash))
    }

    @Test
    fun `lookup known member with pending status based on ledger public key hash`() {
        mockMemberList(listOf(aliceActiveMemberInfo, alicePendingMemberInfo))
        assertEquals(
            alicePendingMemberInfo,
            membershipGroupReaderImpl.lookupByLedgerKey(mockLedgerKeyHash, MembershipStatusFilter.PENDING)
        )
    }

    @Test
    fun `lookup known member with suspended status based on ledger public key hash`() {
        mockMemberList(listOf(aliceSuspendedMemberInfo, alicePendingMemberInfo))
        assertEquals(
            aliceSuspendedMemberInfo,
            membershipGroupReaderImpl.lookupByLedgerKey(mockLedgerKeyHash, MembershipStatusFilter.ACTIVE_OR_SUSPENDED)
        )
    }

    @Test
    fun `lookup non-existing member based on ledger public key hash`() {
        mockMemberList(emptyList())
        assertNull(membershipGroupReaderImpl.lookupByLedgerKey(mockLedgerKeyHash))
    }

    @Test
    fun `lookup member based on ledger public key hash using session key fails`() {
        mockMemberList(listOf(aliceActiveMemberInfo))
        assertNull(membershipGroupReaderImpl.lookupByLedgerKey(mockSessionKeyHash))
    }

    @Test
    fun `lookup known member with active status based on session public key hash`() {
        mockMemberList(listOf(aliceActiveMemberInfo, aliceSuspendedMemberInfo))
        assertEquals(aliceActiveMemberInfo, membershipGroupReaderImpl.lookupBySessionKey(mockSessionKeyHash))
    }

    @Test
    fun `lookup known member with suspended status based on session public key hash`() {
        mockMemberList(listOf(aliceSuspendedMemberInfo, alicePendingMemberInfo))
        assertEquals(
            aliceSuspendedMemberInfo,
            membershipGroupReaderImpl.lookupBySessionKey(mockSessionKeyHash, MembershipStatusFilter.ACTIVE_OR_SUSPENDED)
        )
    }

    @Test
    fun `lookup non-existing member based on session public key hash`() {
        mockMemberList(emptyList())
        assertNull(membershipGroupReaderImpl.lookupBySessionKey(mockSessionKeyHash))
    }

    @Test
    fun `lookup member based on session public key hash using ledger key fails`() {
        mockMemberList(listOf(aliceActiveMemberInfo))
        assertNull(membershipGroupReaderImpl.lookupBySessionKey(mockLedgerKeyHash))
    }

    @Test
    fun `lookup returns pending members only`() {
        mockMemberList(listOf(aliceSuspendedMemberInfo, alicePendingMemberInfo, bobSuspendedMemberInfo))
        assertEquals(listOf(alicePendingMemberInfo), membershipGroupReaderImpl.lookup(MembershipStatusFilter.PENDING))
    }

    @Test
    fun `lookup returns active or suspended members only`() {
        mockMemberList(listOf(aliceActiveMemberInfo, alicePendingMemberInfo, bobSuspendedMemberInfo))
        assertEquals(
            listOf(aliceActiveMemberInfo, bobSuspendedMemberInfo),
            membershipGroupReaderImpl.lookup(MembershipStatusFilter.ACTIVE_OR_SUSPENDED)
        )
    }

    @Test
    fun `lookup returns active members only`() {
        mockMemberList(listOf(aliceActiveMemberInfo, alicePendingMemberInfo, bobSuspendedMemberInfo))
        assertEquals(
            listOf(aliceActiveMemberInfo),
            membershipGroupReaderImpl.lookup()
        )
    }

    @Test
    fun `lookup with falling back on pending status if no active state is present returns the correct list`() {
        mockMemberList(listOf(alicePendingMemberInfo, aliceSuspendedMemberInfo, bobPendingMemberInfo))
        assertEquals(
            listOf(alicePendingMemberInfo, bobPendingMemberInfo),
            membershipGroupReaderImpl.lookup(MembershipStatusFilter.ACTIVE_IF_PRESENT_OR_PENDING)
        )
        mockMemberList(listOf(aliceActiveMemberInfo, alicePendingMemberInfo, bobPendingMemberInfo))
        assertEquals(
            listOf(aliceActiveMemberInfo, bobPendingMemberInfo),
            membershipGroupReaderImpl.lookup(MembershipStatusFilter.ACTIVE_IF_PRESENT_OR_PENDING)
        )
    }

    @Test
    fun `lookup with falling back on pending status if no active state is present returns empty list`() {
        mockMemberList(listOf(aliceSuspendedMemberInfo, bobSuspendedMemberInfo))
        assertThat(membershipGroupReaderImpl.lookup(MembershipStatusFilter.ACTIVE_IF_PRESENT_OR_PENDING)).isEmpty()
    }

    @Test
    fun `lookup throws illegal state exception if no cached member list available`() {
        val error = assertThrows<IllegalStateException> { membershipGroupReaderImpl.lookup() }
        assertEquals(
            "Failed to find member list for ID='${aliceIdGroup1.shortHash}, Group ID='${aliceIdGroup1.groupId}'",
            error.message
        )
    }

    @Test
    fun `group parameters are returned as expected`() {
        assertThat(membershipGroupReaderImpl.groupParameters).isEqualTo(groupParameters)
        verify(groupParametersReaderService, times(1)).get(eq(aliceIdGroup1))
    }
}
