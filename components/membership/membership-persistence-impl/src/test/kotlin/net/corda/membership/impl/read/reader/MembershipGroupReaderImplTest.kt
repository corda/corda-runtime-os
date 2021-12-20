package net.corda.membership.impl.read.reader

import net.corda.membership.GroupPolicy
import net.corda.membership.impl.read.cache.MemberListCache
import net.corda.membership.impl.read.cache.MembershipGroupReadCache
import net.corda.v5.membership.identity.MemberInfo
import net.corda.v5.membership.identity.MemberX500Name
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class MembershipGroupReaderImplTest {
    private lateinit var membershipGroupReaderImpl: MembershipGroupReaderImpl

    private val groupId = "GROUP_ID"
    private val memberName = MemberX500Name("Alice", "London", "GB")
    private val groupPolicy: GroupPolicy = mock()
    private val memberCache: MemberListCache = mock()
    private val membershipGroupCache: MembershipGroupReadCache = mock<MembershipGroupReadCache>().apply {
        whenever(this.memberListCache).thenReturn(memberCache)
    }
    private val memberInfo = mock<MemberInfo>().apply {
        whenever(name).thenReturn(memberName)
    }

    @BeforeEach
    fun setUp() {
        membershipGroupReaderImpl = MembershipGroupReaderImpl(
            groupId,
            memberName,
            groupPolicy,
            membershipGroupCache
        )
    }

    private fun mockMemberList(memberList: List<MemberInfo>) {
        whenever(memberCache.get(eq(groupId), eq(memberName))).thenReturn(memberList)
    }

    @Test
    fun `lookup known member based on name`() {
        mockMemberList(listOf(memberInfo))
        assertEquals(memberInfo, membershipGroupReaderImpl.lookup(memberName))
    }

    @Test
    fun `lookup non-existing member based on name`() {
        mockMemberList(emptyList())
        assertNull(membershipGroupReaderImpl.lookup(memberName))
    }
}