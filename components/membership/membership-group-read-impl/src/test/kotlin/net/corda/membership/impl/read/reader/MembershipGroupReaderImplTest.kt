package net.corda.membership.impl.read.reader

import net.corda.membership.impl.MemberInfoExtension.Companion.IDENTITY_KEY_HASHES
import net.corda.membership.impl.read.TestProperties
import net.corda.membership.impl.read.TestProperties.Companion.GROUP_ID_1
import net.corda.membership.impl.read.cache.MemberListCache
import net.corda.membership.impl.read.cache.MembershipGroupReadCache
import net.corda.v5.crypto.PublicKeyHash
import net.corda.v5.crypto.sha256Bytes
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.PublicKey

class MembershipGroupReaderImplTest {
    private lateinit var membershipGroupReaderImpl: MembershipGroupReaderImpl

    private val aliceName = TestProperties.aliceName
    private val aliceIdGroup1 = HoldingIdentity(aliceName.toString(), GROUP_ID_1)
    private val memberCache: MemberListCache = mock()
    private val membershipGroupCache: MembershipGroupReadCache = mock<MembershipGroupReadCache>().apply {
        whenever(this.memberListCache).thenReturn(memberCache)
    }
    private val knownKey: PublicKey = mock()
    private val knownKeyAsByteArray = "1234".toByteArray()
    private val knownKeyHash = PublicKeyHash.parse(knownKeyAsByteArray.sha256Bytes())
    private val memberInfo: MemberInfo = mock {
        on { name } doReturn aliceName
        on { identityKeys } doReturn listOf(knownKey)
        val mockedMemberProvidedContext = mock<MemberContext> {
            on { parseSet(eq(IDENTITY_KEY_HASHES), eq(PublicKeyHash::class.java)) } doReturn setOf(knownKeyHash)
        }
        on { memberProvidedContext } doReturn mockedMemberProvidedContext

    }

    @BeforeEach
    fun setUp() {
        membershipGroupReaderImpl = MembershipGroupReaderImpl(
            aliceIdGroup1,
            membershipGroupCache
        )
    }

    private fun mockMemberList(memberList: List<MemberInfo>) {
        whenever(memberCache.get(eq(aliceIdGroup1))).thenReturn(memberList)
    }

    @Test
    fun `lookup known member based on name`() {
        mockMemberList(listOf(memberInfo))
        assertEquals(memberInfo, membershipGroupReaderImpl.lookup(aliceName))
    }

    @Test
    fun `lookup non-existing member based on name`() {
        mockMemberList(emptyList())
        assertNull(membershipGroupReaderImpl.lookup(aliceName))
    }

    @Test
    fun `lookup known member based on public key hash`() {
        mockMemberList(listOf(memberInfo))
        assertEquals(memberInfo, membershipGroupReaderImpl.lookup(knownKeyHash))
    }

    @Test
    fun `lookup non-existing member based on public key hash`() {
        mockMemberList(emptyList())
        assertNull(membershipGroupReaderImpl.lookup(knownKeyHash))
    }
}
