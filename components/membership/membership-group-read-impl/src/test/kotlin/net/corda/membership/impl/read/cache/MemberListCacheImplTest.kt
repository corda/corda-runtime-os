package net.corda.membership.impl.read.cache

import net.corda.v5.membership.identity.MemberInfo
import net.corda.v5.membership.identity.MemberX500Name
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class MemberListCacheImplTest {
    private lateinit var memberListCache: MemberListCache

    private val groupId1 = "GROUP_ID1"
    private val groupId2 = "GROUP_ID2"
    private val lookUpMemberName = MemberX500Name("Alice", "London", "GB")
    private val memberName1 = MemberX500Name("Bob", "London", "GB")
    private val memberName2 = MemberX500Name("Charlie", "London", "GB")
    private val memberInfo1 = mock<MemberInfo>().apply {
        whenever(name).thenReturn(memberName1)
    }
    private val memberInfo2 = mock<MemberInfo>().apply {
        whenever(name).thenReturn(memberName2)
    }

    @BeforeEach
    fun setUp() {
        memberListCache = MemberListCache.Impl()
    }

    @Test
    fun `Get member list before any data is cached`() {
        assertMemberList(lookupWithDefaults())
    }

    @Test
    fun `Add member list to cache then read same member from cache`() {
        memberListCache.put(groupId1, lookUpMemberName, listOf(memberInfo1))
        assertMemberList(lookupWithDefaults(), memberInfo1)
    }

    @Test
    fun `Add single member to cache then read same member from cache`() {
        addToCacheWithDefaults()
        assertMemberList(lookupWithDefaults(), memberInfo1)
    }

    @Test
    fun `Cache for one member and lookup for a different member`() {
        addToCacheWithDefaults(lookUpMember = memberName1)
        assertMemberList(lookupWithDefaults())
    }

    @Test
    fun `Cache for one group and lookup for a different group`() {
        addToCacheWithDefaults()
        assertMemberList(lookupWithDefaults(groupId = groupId2))
    }

    @Test
    fun `Cache and lookup for a multiple groups`() {
        addToCacheWithDefaults()
        addToCacheWithDefaults(groupId = groupId2, memberInfo = memberInfo2)

        assertMemberList(lookupWithDefaults(), memberInfo1)
        assertMemberList(lookupWithDefaults(groupId = groupId2), memberInfo2)
    }

    @Test
    fun `Cache and lookup for multiple members in the same group`() {
        addToCacheWithDefaults()
        addToCacheWithDefaults(lookUpMember = memberName1, memberInfo = memberInfo2)

        assertMemberList(lookupWithDefaults(), memberInfo1)
        assertMemberList(lookupWithDefaults(lookUpMember = memberName1), memberInfo2)
    }

    private fun lookupWithDefaults(
        groupId: String = groupId1,
        lookUpMember: MemberX500Name = lookUpMemberName
    ): List<MemberInfo>? {
        return memberListCache.get(groupId, lookUpMember)
    }

    private fun addToCacheWithDefaults(
        groupId: String = groupId1,
        lookUpMember: MemberX500Name = lookUpMemberName,
        memberInfo: MemberInfo = memberInfo1
    ) {
        memberListCache.put(groupId, lookUpMember, memberInfo)
    }

    private fun assertMemberList(memberList: List<MemberInfo>?, expectedMemberInfo: MemberInfo? = null) {
        assertNotNull(memberList)
        if (expectedMemberInfo == null) {
            assertEquals(0, memberList!!.size)
        } else {
            assertEquals(1, memberList!!.size)
            assertEquals(expectedMemberInfo, memberList[0])
        }
    }
}
