package net.corda.membership.impl.read.cache

import net.corda.membership.impl.read.TestProperties.Companion.GROUP_ID_1
import net.corda.membership.impl.read.TestProperties.Companion.GROUP_ID_2
import net.corda.membership.impl.read.TestProperties.Companion.aliceName
import net.corda.membership.impl.read.TestProperties.Companion.bobName
import net.corda.membership.impl.read.TestProperties.Companion.charlieName
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class MemberListCacheImplTest {
    private lateinit var memberListCache: MemberListCache

    private val alice = aliceName
    private val bob = bobName
    private val charlie = charlieName

    private val aliceIdGroup1 = HoldingIdentity(alice.toString(), GROUP_ID_1)
    private val bobIdGroup1 = HoldingIdentity(bob.toString(), GROUP_ID_1)
    private val aliceIdGroup2 = HoldingIdentity(alice.toString(), GROUP_ID_2)

    private val memberInfo1 = mock<MemberInfo>().apply {
        whenever(name).thenReturn(bob)
    }
    private val memberInfo2 = mock<MemberInfo>().apply {
        whenever(name).thenReturn(charlie)
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
        memberListCache.put(aliceIdGroup1, listOf(memberInfo1))
        assertMemberList(lookupWithDefaults(), memberInfo1)
    }

    @Test
    fun `Add single member to cache then read same member from cache`() {
        addToCacheWithDefaults()
        assertMemberList(lookupWithDefaults(), memberInfo1)
    }

    @Test
    fun `Cache for one member and lookup for a different member`() {
        addToCacheWithDefaults()
        assertMemberList(lookupWithDefaults(bobIdGroup1))
    }

    @Test
    fun `Cache for one group and lookup for a different empty group`() {
        addToCacheWithDefaults()
        assertMemberList(lookupWithDefaults(aliceIdGroup2))
    }

    @Test
    fun `Cache and lookup for a multiple groups`() {
        addToCacheWithDefaults()
        addToCacheWithDefaults(aliceIdGroup2, memberInfo = memberInfo2)

        assertMemberList(lookupWithDefaults(), memberInfo1)
        assertMemberList(lookupWithDefaults(aliceIdGroup2), memberInfo2)
    }

    @Test
    fun `Cache and lookup for multiple members in the same group`() {
        addToCacheWithDefaults()
        addToCacheWithDefaults(bobIdGroup1, memberInfo = memberInfo2)

        assertMemberList(lookupWithDefaults(), memberInfo1)
        assertMemberList(lookupWithDefaults(bobIdGroup1), memberInfo2)
    }

    @Test
    fun `Cache output cannot be cast to mutable list`() {
        addToCacheWithDefaults()
        val lookupResult = lookupWithDefaults()
        assertThrows<ClassCastException> {
            lookupResult as MutableList<MemberInfo>
        }
    }

    private fun lookupWithDefaults(
        holdingIdentity: HoldingIdentity = aliceIdGroup1
    ): List<MemberInfo>? {
        return memberListCache.get(holdingIdentity)
    }

    private fun addToCacheWithDefaults(
        holdingIdentity: HoldingIdentity = aliceIdGroup1,
        memberInfo: MemberInfo = memberInfo1
    ) {
        memberListCache.put(holdingIdentity, memberInfo)
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
