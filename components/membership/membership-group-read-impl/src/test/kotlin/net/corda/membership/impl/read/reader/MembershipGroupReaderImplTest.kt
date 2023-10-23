package net.corda.membership.impl.read.reader

import net.corda.crypto.cipher.suite.sha256Bytes
import net.corda.crypto.core.SecureHashImpl
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.data.p2p.app.MembershipStatusFilter.ACTIVE_OR_SUSPENDED_IF_PRESENT_OR_PENDING
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.membership.impl.read.TestProperties
import net.corda.membership.impl.read.TestProperties.Companion.GROUP_ID_1
import net.corda.membership.impl.read.cache.MemberListCache
import net.corda.membership.impl.read.cache.MembershipGroupReadCache
import net.corda.membership.lib.MemberInfoExtension.Companion.GROUP_ID
import net.corda.membership.lib.MemberInfoExtension.Companion.IS_MGM
import net.corda.membership.lib.MemberInfoExtension.Companion.LEDGER_KEYS
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_PENDING
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED
import net.corda.membership.lib.MemberInfoExtension.Companion.MODIFIED_TIME
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_NAME
import net.corda.membership.lib.MemberInfoExtension.Companion.PARTY_SESSION_KEYS
import net.corda.membership.lib.MemberInfoExtension.Companion.PLATFORM_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.PROTOCOL_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.SERIAL
import net.corda.membership.lib.MemberInfoExtension.Companion.SESSION_KEYS
import net.corda.membership.lib.MemberInfoExtension.Companion.SOFTWARE_VERSION
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.MemberInfoExtension.Companion.URL_KEY
import net.corda.membership.lib.MemberInfoExtension.Companion.isMgm
import net.corda.membership.lib.MemberInfoFactory
import net.corda.membership.lib.SignedGroupParameters
import net.corda.membership.lib.UnsignedGroupParameters
import net.corda.membership.read.GroupParametersReaderService
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.security.PublicKey
import java.time.Instant
import java.util.SortedMap

class MembershipGroupReaderImplTest {
    private lateinit var membershipGroupReaderImpl: MembershipGroupReaderImpl

    private val aliceName = TestProperties.aliceName
    private val aliceIdGroup1 = HoldingIdentity(aliceName, GROUP_ID_1)
    private val bobName = TestProperties.bobName
    private val bobIdGroup1 = HoldingIdentity(bobName, GROUP_ID_1)
    private val memberCache: MemberListCache = mock()
    private val membershipGroupCache: MembershipGroupReadCache = mock<MembershipGroupReadCache>().apply {
        whenever(this.memberListCache).thenReturn(memberCache)
    }
    private val unsignedGroupParameters: UnsignedGroupParameters = mock()
    private val signedGroupParameters: SignedGroupParameters = mock()
    private val groupParametersReaderService: GroupParametersReaderService = mock {
        on { get(eq(aliceIdGroup1)) } doReturn unsignedGroupParameters
        on { getSigned(eq(bobIdGroup1)) } doReturn signedGroupParameters
    }
    private val mockLedgerKey: PublicKey = mock()
    private val mockLedgerKeyAsByteArray = "1234".toByteArray()
    private val mockLedgerKeyHash =
        SecureHashImpl(DigestAlgorithmName.SHA2_256.name, mockLedgerKeyAsByteArray.sha256Bytes())
    private val mockSessionKey: PublicKey = mock()
    private val mockSessionKeyAsByteArray = "5678".toByteArray()
    private val mockSessionKeyHash =
        SecureHashImpl(DigestAlgorithmName.SHA2_256.name, mockSessionKeyAsByteArray.sha256Bytes())
    private val mockedSuspendedMemberProvidedContext = mock<MemberContext> {
        on { parseSet(eq(LEDGER_KEYS), eq(SecureHash::class.java)) } doReturn setOf(mockLedgerKeyHash)
        on { parseSet(eq(SESSION_KEYS), eq(SecureHash::class.java)) } doReturn setOf(mockSessionKeyHash)
    }
    private val mockedSuspendedMgmProvidedContext = mock<MGMContext> {
        on { parse(eq(STATUS), eq(String::class.java)) } doReturn MEMBER_STATUS_SUSPENDED
        on { parseList(SESSION_KEYS, PublicKey::class.java) } doReturn listOf(mockSessionKey)
    }
    private val aliceSuspendedMemberInfo: MemberInfo = mock {
        on { name } doReturn aliceName
        on { ledgerKeys } doReturn listOf(mockLedgerKey)
        on { memberProvidedContext } doReturn mockedSuspendedMemberProvidedContext
        on { mgmProvidedContext } doReturn mockedSuspendedMgmProvidedContext
        on { isActive } doReturn false
    }
    private val bobSuspendedMemberInfo: MemberInfo = mock {
        on { name } doReturn bobName
        on { ledgerKeys } doReturn listOf(mockLedgerKey)
        on { memberProvidedContext } doReturn mockedSuspendedMemberProvidedContext
        on { mgmProvidedContext } doReturn mockedSuspendedMgmProvidedContext
        on { isActive } doReturn false
    }

    private val mockedActiveMemberProvidedContext = mock<MemberContext> {
        on { parseSet(eq(LEDGER_KEYS), eq(SecureHash::class.java)) } doReturn setOf(mockLedgerKeyHash)
        on { parseSet(eq(SESSION_KEYS), eq(SecureHash::class.java)) } doReturn setOf(mockSessionKeyHash)
        on { parse(eq(STATUS), eq(String::class.java)) } doReturn MEMBER_STATUS_ACTIVE
        on { parseList(SESSION_KEYS, PublicKey::class.java) } doReturn listOf(mockSessionKey)
    }
    private val mockedActiveMgmProvidedContext = mock<MGMContext> {
        on { parse(eq(STATUS), eq(String::class.java)) } doReturn MEMBER_STATUS_ACTIVE
    }
    private val aliceActiveMemberInfo: MemberInfo = mock {
        on { name } doReturn aliceName
        on { ledgerKeys } doReturn listOf(mockLedgerKey)
        on { memberProvidedContext } doReturn mockedActiveMemberProvidedContext
        on { mgmProvidedContext } doReturn mockedActiveMgmProvidedContext
        on { isActive } doReturn true
    }
    private val bobActiveMemberInfo: MemberInfo = mock {
        on { name } doReturn bobName
        on { ledgerKeys } doReturn listOf(mockLedgerKey)
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
            groupParametersReaderService,
            mock(),
            mock(),
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
        assertEquals(
            alicePendingMemberInfo,
            membershipGroupReaderImpl.lookup(aliceName, MembershipStatusFilter.PENDING)
        )
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
    fun `ACTIVE_OR_SUSPENDED_IF_PRESENT_OR_PENDING filter returns active instead of pending`() {
        val allMembers = listOf(aliceActiveMemberInfo, alicePendingMemberInfo, bobSuspendedMemberInfo)
        mockMemberList(allMembers)
        assertEquals(
            listOf(aliceActiveMemberInfo, bobSuspendedMemberInfo),
            membershipGroupReaderImpl.lookup(ACTIVE_OR_SUSPENDED_IF_PRESENT_OR_PENDING)
        )
    }

    @Test
    fun `ACTIVE_OR_SUSPENDED_IF_PRESENT_OR_PENDING filter returns suspended instead of pending`() {
        val allMembers = listOf(aliceSuspendedMemberInfo, alicePendingMemberInfo, bobSuspendedMemberInfo)
        mockMemberList(allMembers)
        assertEquals(
            listOf(aliceSuspendedMemberInfo, bobSuspendedMemberInfo),
            membershipGroupReaderImpl.lookup(ACTIVE_OR_SUSPENDED_IF_PRESENT_OR_PENDING)
        )
    }

    @Test
    fun `ACTIVE_OR_SUSPENDED_IF_PRESENT_OR_PENDING filter returns pending if not active or suspended is available`() {
        val allMembers = listOf(alicePendingMemberInfo, bobSuspendedMemberInfo)
        mockMemberList(allMembers)
        assertEquals(
            listOf(alicePendingMemberInfo, bobSuspendedMemberInfo),
            membershipGroupReaderImpl.lookup(ACTIVE_OR_SUSPENDED_IF_PRESENT_OR_PENDING)
        )
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
        assertThat(membershipGroupReaderImpl.groupParameters).isEqualTo(unsignedGroupParameters)
        verify(groupParametersReaderService).get(eq(aliceIdGroup1))
    }

    @Test
    fun `signed group parameters are returned as expected`() {
        val bobGroupReader = MembershipGroupReaderImpl(
            bobIdGroup1,
            membershipGroupCache,
            groupParametersReaderService,
            mock(),
            mock(),
        )
        assertThat(bobGroupReader.signedGroupParameters).isEqualTo(signedGroupParameters)
        verify(groupParametersReaderService).getSigned(eq(bobIdGroup1))
    }

    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Nested
    inner class MGMLookupTests {
        private val mgmName = TestProperties.charlieName
        private val mgmIdGroup1 = HoldingIdentity(mgmName, GROUP_ID_1)
        private val platformInfoProvider: PlatformInfoProvider = mock {
            on { activePlatformVersion } doReturn 50100
        }
        private val memberContextMap: SortedMap<String, String?> = sortedMapOf(
            PARTY_NAME to mgmName.toString(),
            String.format(PARTY_SESSION_KEYS, 0) to "1234",
            GROUP_ID to GROUP_ID_1,
            URL_KEY.format(0) to "https://corda5.r3.com:10000",
            PROTOCOL_VERSION.format(0) to "1",
            SOFTWARE_VERSION to "5.0.0",
            PLATFORM_VERSION to platformInfoProvider.activePlatformVersion.toString(),
        )
        private val mgmContextMap = sortedMapOf(
            STATUS to MEMBER_STATUS_ACTIVE,
            MODIFIED_TIME to Instant.now().toString(),
            IS_MGM to "true",
            SERIAL to "1",
        )
        private val mgmMemberInfo: MemberInfo = mock {
            on { name } doReturn mgmName
            on { memberProvidedContext } doReturn mockedActiveMemberProvidedContext
            on { mgmProvidedContext } doReturn mockedActiveMgmProvidedContext
            on { isActive } doReturn true
            on { isMgm } doReturn true
        }
        private val mgmWithLatestPlatformVersion: MemberInfo = mock()
        private val memberInfoFactory: MemberInfoFactory = mock {
            on { createMemberInfo(eq(memberContextMap), any()) } doReturn mgmWithLatestPlatformVersion
        }
        private lateinit var mgmGroupReader: MembershipGroupReaderImpl

        @BeforeAll
        fun setup() {
            whenever(mockedActiveMemberProvidedContext.entries).doReturn(memberContextMap.entries)
            whenever(mockedActiveMgmProvidedContext.entries).doReturn(mgmContextMap.entries)
            mgmGroupReader = MembershipGroupReaderImpl(
                mgmIdGroup1,
                membershipGroupCache,
                groupParametersReaderService,
                memberInfoFactory,
                platformInfoProvider,
            )
        }

        @Test
        fun `lookup performed by MGM returns active platform version in its own MemberInfo`() {
            whenever(memberCache.get(eq(mgmIdGroup1))).thenReturn(listOf(aliceActiveMemberInfo, mgmMemberInfo))
            assertThat(mgmGroupReader.lookup())
                .containsExactlyInAnyOrder(mgmWithLatestPlatformVersion, aliceActiveMemberInfo)
        }

        @Test
        fun `lookup performed by MGM based on name returns active platform version in its own MemberInfo`() {
            whenever(memberCache.get(eq(mgmIdGroup1))).thenReturn(listOf(aliceActiveMemberInfo, mgmMemberInfo))
            assertThat(mgmGroupReader.lookup(mgmName)).isEqualTo(mgmWithLatestPlatformVersion)
        }

        @Test
        fun `lookup performed by MGM based on session key hash returns active platform version in its own MemberInfo`() {
            whenever(memberCache.get(eq(mgmIdGroup1))).thenReturn(listOf(mgmMemberInfo))
            assertThat(mgmGroupReader.lookupBySessionKey(mockSessionKeyHash)).isEqualTo(mgmWithLatestPlatformVersion)
        }
    }
}
