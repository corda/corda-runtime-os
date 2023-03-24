package net.corda.membership.impl.read.cache

import net.corda.membership.impl.read.TestProperties.Companion.GROUP_ID_1
import net.corda.membership.impl.read.TestProperties.Companion.GROUP_ID_2
import net.corda.membership.impl.read.TestProperties.Companion.aliceName
import net.corda.membership.impl.read.TestProperties.Companion.bobName
import net.corda.membership.impl.read.TestProperties.Companion.charlieName
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_PENDING
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.v5.membership.MGMContext
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class MemberListCacheImplTest {
    private lateinit var memberListCache: MemberListCache

    private val alice = aliceName
    private val bob = bobName
    private val charlie = charlieName

    private val aliceIdGroup1 = HoldingIdentity(alice, GROUP_ID_1)
    private val bobIdGroup1 = HoldingIdentity(bob, GROUP_ID_1)
    private val aliceIdGroup2 = HoldingIdentity(alice, GROUP_ID_2)

    private val pendingContext = mock<MGMContext> {
        on { parse(STATUS, String::class.java) } doReturn MEMBER_STATUS_PENDING
    }
    private val activeContext = mock<MGMContext> {
        on { parse(STATUS, String::class.java) } doReturn MEMBER_STATUS_ACTIVE
    }
    private val suspendedContext = mock<MGMContext> {
        on { parse(STATUS, String::class.java) } doReturn MEMBER_STATUS_SUSPENDED
    }

    private val bobInfo = mock<MemberInfo> {
        on { mgmProvidedContext } doReturn activeContext
        on { name } doReturn bob
    }
    private val aliceInfo = mock<MemberInfo> {
        on { mgmProvidedContext } doReturn activeContext
        on { name } doReturn alice
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
        memberListCache.put(aliceIdGroup1, listOf(bobInfo))
        assertMemberList(lookupWithDefaults(), bobInfo)
    }

    @Test
    fun `Add single member to cache then read same member from cache`() {
        addToCacheWithDefaults()
        assertMemberList(lookupWithDefaults(), bobInfo)
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
        addToCacheWithDefaults(aliceIdGroup2, memberInfo = aliceInfo)

        assertMemberList(lookupWithDefaults(), bobInfo)
        assertMemberList(lookupWithDefaults(aliceIdGroup2), aliceInfo)
    }

    @Test
    fun `get all returns all information from cache`() {
        addToCacheWithDefaults()
        addToCacheWithDefaults(aliceIdGroup2, memberInfo = aliceInfo)

        val cache = memberListCache.getAll()
        assertThat(cache.size).isEqualTo(2)
        assertMemberList(cache.get(aliceIdGroup1), bobInfo)
        assertMemberList(cache.get(aliceIdGroup2), aliceInfo)
    }

    @Test
    fun `Cache and lookup for multiple members in the same group`() {
        addToCacheWithDefaults()
        addToCacheWithDefaults(bobIdGroup1, memberInfo = aliceInfo)

        assertMemberList(lookupWithDefaults(), bobInfo)
        assertMemberList(lookupWithDefaults(bobIdGroup1), aliceInfo)
    }

    @Test
    fun `Cache output cannot be cast to mutable list`() {
        addToCacheWithDefaults()
        val lookupResult = lookupWithDefaults()
        assertThrows<ClassCastException> {
            lookupResult as MutableList<MemberInfo>
        }
    }

    @Test
    fun `Member info discarded if status is the same - active`() {
        val originalInfo = mock<MemberInfo> {
            on { mgmProvidedContext } doReturn activeContext
            on { name } doReturn charlie

        }
        val updatedInfo = mock<MemberInfo> {
            on { mgmProvidedContext } doReturn activeContext
            on { name } doReturn charlie
        }

        addToCacheWithDefaults(memberInfo = originalInfo)
        addToCacheWithDefaults(memberInfo = updatedInfo)
        assertMemberList(lookupWithDefaults(), updatedInfo)
    }

    @Test
    fun `Member info discarded if status is the same - pending`() {
        val originalInfo = mock<MemberInfo> {
            on { mgmProvidedContext } doReturn pendingContext
            on { name } doReturn charlie
        }
        val updatedInfo = mock<MemberInfo> {
            on { mgmProvidedContext } doReturn pendingContext
            on { name } doReturn charlie
        }

        addToCacheWithDefaults(memberInfo = originalInfo)
        addToCacheWithDefaults(memberInfo = updatedInfo)
        assertMemberList(lookupWithDefaults(), updatedInfo)
    }

    @Test
    fun `Member info discarded if status is active or suspended`() {
        val activeInfo = mock<MemberInfo> {
            on { mgmProvidedContext } doReturn activeContext
            on { name } doReturn charlie
        }
        val suspendedInfo = mock<MemberInfo> {
            on { mgmProvidedContext } doReturn suspendedContext
            on { name } doReturn charlie
        }

        addToCacheWithDefaults(memberInfo = activeInfo)
        // suspend member
        addToCacheWithDefaults(memberInfo = suspendedInfo)
        assertMemberList(lookupWithDefaults(), suspendedInfo)
        // activate member
        addToCacheWithDefaults(memberInfo = activeInfo)
        assertMemberList(lookupWithDefaults(), activeInfo)
    }

    @Test
    fun `Member info not discarded if status is pending and then active`() {
        val originalInfo = mock<MemberInfo> {
            on { mgmProvidedContext } doReturn pendingContext
            on { name } doReturn charlie
        }
        val updatedInfo = mock<MemberInfo> {
            on { mgmProvidedContext } doReturn activeContext
            on { name } doReturn charlie
        }

        addToCacheWithDefaults(memberInfo = originalInfo)
        addToCacheWithDefaults(memberInfo = updatedInfo)
        // add extra member
        addToCacheWithDefaults(memberInfo = bobInfo)
        assertThat(lookupWithDefaults()).containsExactlyInAnyOrder(bobInfo, originalInfo, updatedInfo)
    }

    private fun lookupWithDefaults(
        holdingIdentity: HoldingIdentity = aliceIdGroup1
    ): List<MemberInfo>? {
        return memberListCache.get(holdingIdentity)
    }

    private fun addToCacheWithDefaults(
        holdingIdentity: HoldingIdentity = aliceIdGroup1,
        memberInfo: MemberInfo = bobInfo
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
