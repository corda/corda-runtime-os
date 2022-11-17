package net.corda.membership.impl.read.reader

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
import net.corda.membership.read.GroupParametersReaderService
import net.corda.v5.crypto.PublicKeyHash
import net.corda.v5.crypto.sha256Bytes
import net.corda.v5.membership.GroupParameters
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
    private val memberCache: MemberListCache = mock()
    private val membershipGroupCache: MembershipGroupReadCache = mock<MembershipGroupReadCache>().apply {
        whenever(this.memberListCache).thenReturn(memberCache)
    }
    private val groupParameters: GroupParameters = mock()
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
    private val suspendedMemberInfo: MemberInfo = mock {
        on { name } doReturn aliceName
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
    private val activeMemberInfo: MemberInfo = mock {
        on { name } doReturn aliceName
        on { ledgerKeys } doReturn listOf(mockLedgerKey)
        on { sessionInitiationKey } doReturn mockSessionKey
        on { memberProvidedContext } doReturn mockedActiveMemberProvidedContext
        on { mgmProvidedContext } doReturn mockedActiveMgmProvidedContext
        on { isActive } doReturn true
    }

    private val mockedPendingMgmProvidedContext = mock<MGMContext> {
        on { parse(eq(STATUS), eq(String::class.java)) } doReturn MEMBER_STATUS_PENDING
    }
    private val pendingMemberInfo: MemberInfo = mock {
        on { name } doReturn aliceName
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
        mockMemberList(listOf(activeMemberInfo))
        assertEquals(activeMemberInfo, membershipGroupReaderImpl.lookup(aliceName))
    }

    @Test
    fun `lookup known member with non active status based on name`() {
        mockMemberList(listOf(suspendedMemberInfo))
        assertNull(membershipGroupReaderImpl.lookup(aliceName))
    }

    @Test
    fun `lookup known member with pending status based on name`() {
        mockMemberList(listOf(pendingMemberInfo))
        assertEquals(pendingMemberInfo, membershipGroupReaderImpl.lookup(aliceName))
    }

    @Test
    fun `lookup non-existing member based on name`() {
        mockMemberList(emptyList())
        assertNull(membershipGroupReaderImpl.lookup(aliceName))
    }

    @Test
    fun `lookup known member with active status based on ledger public key hash`() {
        mockMemberList(listOf(activeMemberInfo))
        assertEquals(activeMemberInfo, membershipGroupReaderImpl.lookupByLedgerKey(mockLedgerKeyHash))
    }

    @Test
    fun `lookup known member with non active status based on ledger public key hash`() {
        mockMemberList(listOf(suspendedMemberInfo))
        assertNull(membershipGroupReaderImpl.lookupByLedgerKey(mockLedgerKeyHash))
    }

    @Test
    fun `lookup non-existing member based on ledger public key hash`() {
        mockMemberList(emptyList())
        assertNull(membershipGroupReaderImpl.lookupByLedgerKey(mockLedgerKeyHash))
    }

    @Test
    fun `lookup member based on ledger public key hash using session key fails`() {
        mockMemberList(listOf(activeMemberInfo))
        assertNull(membershipGroupReaderImpl.lookupByLedgerKey(mockSessionKeyHash))
    }

    @Test
    fun `lookup known member with active status based on session public key hash`() {
        mockMemberList(listOf(activeMemberInfo))
        assertEquals(activeMemberInfo, membershipGroupReaderImpl.lookupBySessionKey(mockSessionKeyHash))
    }

    @Test
    fun `lookup known member with non active status based on session public key hash`() {
        mockMemberList(listOf(suspendedMemberInfo))
        assertNull(membershipGroupReaderImpl.lookupBySessionKey(mockSessionKeyHash))
    }

    @Test
    fun `lookup non-existing member based on session public key hash`() {
        mockMemberList(emptyList())
        assertNull(membershipGroupReaderImpl.lookupBySessionKey(mockSessionKeyHash))
    }

    @Test
    fun `lookup member based on session public key hash using ledger key fails`() {
        mockMemberList(listOf(activeMemberInfo))
        assertNull(membershipGroupReaderImpl.lookupBySessionKey(mockLedgerKeyHash))
    }

    @Test
    fun `lookup returns active members only`() {
        mockMemberList(listOf(suspendedMemberInfo, activeMemberInfo))
        assertEquals(listOf(activeMemberInfo), membershipGroupReaderImpl.lookup())
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
        verify(groupParametersReaderService.get(eq(aliceIdGroup1)), times(1))
    }
}
