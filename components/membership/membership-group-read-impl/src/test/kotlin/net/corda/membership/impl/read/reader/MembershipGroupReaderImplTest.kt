package net.corda.membership.impl.read.reader

import net.corda.membership.GroupPolicy
import net.corda.membership.impl.read.TestProperties
import net.corda.membership.impl.read.TestProperties.Companion.GROUP_ID_1
import net.corda.membership.impl.read.cache.MemberListCache
import net.corda.membership.impl.read.cache.MembershipGroupReadCache
import net.corda.v5.membership.identity.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class MembershipGroupReaderImplTest {
    private lateinit var membershipGroupReaderImpl: MembershipGroupReaderImpl

    private val aliceName = TestProperties.aliceName
    private val aliceIdGroup1 = HoldingIdentity(aliceName.toString(), GROUP_ID_1)
    private val groupPolicy: GroupPolicy = mock()
    private val memberCache: MemberListCache = mock()
    private val membershipGroupCache: MembershipGroupReadCache = mock<MembershipGroupReadCache>().apply {
        whenever(this.memberListCache).thenReturn(memberCache)
    }
    private val memberInfo = mock<MemberInfo>().apply {
        whenever(name).thenReturn(aliceName)
    }

    @BeforeEach
    fun setUp() {
        membershipGroupReaderImpl = MembershipGroupReaderImpl(
            aliceIdGroup1,
            groupPolicy,
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
}