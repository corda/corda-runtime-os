package net.corda.membership.impl.read.cache

import net.corda.membership.impl.read.TestProperties
import net.corda.membership.impl.read.TestProperties.Companion.GROUP_ID_1
import net.corda.membership.impl.read.TestProperties.Companion.GROUP_ID_2
import net.corda.v5.membership.identity.MemberX500Name
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

/**
 * Test the member data cache
 */
class MemberDataCacheTest {

    interface MemberData

    private lateinit var memberDataCache: MemberDataCache<MemberData>

    private val lookUpMemberName = TestProperties.aliceName
    private val memberName1 = TestProperties.bobName
    private val memberData1 = mock<MemberData>()
    private val memberData2 = mock<MemberData>()

    @BeforeEach
    fun setUp() {
        memberDataCache = MemberDataCache.Impl()
    }

    @Test
    fun `Get member data before any data is cached`() {
        assertNull(memberDataCache.get(GROUP_ID_1, lookUpMemberName))
    }

    @Test
    fun `Add member data to cache then read same member data from cache`() {
        addToCacheWithDefaults()
        with(lookupWithDefaults()) {
            assertNotNull(this)
            assertEquals(memberData1, this)
        }
    }

    @Test
    fun `Cache for one member and lookup for a different member`() {
        addToCacheWithDefaults()
        assertNull(lookupWithDefaults(lookUpMember = memberName1))
    }

    @Test
    fun `Cache for one group and lookup for a different group`() {
        addToCacheWithDefaults()
        assertNull(lookupWithDefaults(groupId = GROUP_ID_2))
    }

    @Test
    fun `Cache and lookup for a multiple groups`() {
        addToCacheWithDefaults()
        addToCacheWithDefaults(
            groupId = GROUP_ID_2,
            memberData = memberData2
        )

        with(lookupWithDefaults()) {
            assertNotNull(this)
            assertEquals(memberData1, this)
        }

        with(lookupWithDefaults(groupId = GROUP_ID_2)) {
            assertNotNull(this)
            assertEquals(memberData2, this)
        }
    }

    @Test
    fun `Cache and lookup for multiple members in the same group`() {
        addToCacheWithDefaults()
        addToCacheWithDefaults(
            lookUpMember = memberName1,
            memberData = memberData2
        )

        with(lookupWithDefaults()) {
            assertNotNull(this)
            assertEquals(memberData1, this)
        }

        with(lookupWithDefaults(lookUpMember = memberName1)) {
            assertNotNull(this)
            assertEquals(memberData2, this)
        }
    }

    private fun lookupWithDefaults(
        groupId: String = GROUP_ID_1,
        lookUpMember: MemberX500Name = lookUpMemberName,
    ): MemberData? {
        return memberDataCache.get(groupId, lookUpMember)
    }

    private fun addToCacheWithDefaults(
        groupId: String = GROUP_ID_1,
        lookUpMember: MemberX500Name = lookUpMemberName,
        memberData: MemberData = memberData1
    ) {
        memberDataCache.put(groupId, lookUpMember, memberData)
    }
}
