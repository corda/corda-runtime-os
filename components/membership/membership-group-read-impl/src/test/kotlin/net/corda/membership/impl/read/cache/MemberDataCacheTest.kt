package net.corda.membership.impl.read.cache

import net.corda.membership.impl.read.TestProperties
import net.corda.membership.impl.read.TestProperties.Companion.GROUP_ID_1
import net.corda.membership.impl.read.TestProperties.Companion.GROUP_ID_2
import net.corda.virtualnode.HoldingIdentity
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

    private val aliceIdGroup1 = HoldingIdentity(TestProperties.aliceName.toString(), GROUP_ID_1)
    private val bobIdGroup1 = HoldingIdentity(TestProperties.bobName.toString(), GROUP_ID_1)
    private val aliceIdGroup2 = HoldingIdentity(TestProperties.aliceName.toString(), GROUP_ID_2)
    private val memberData1 = mock<MemberData>()
    private val memberData2 = mock<MemberData>()

    @BeforeEach
    fun setUp() {
        memberDataCache = MemberDataCache.Impl()
    }

    @Test
    fun `Get member data before any data is cached`() {
        assertNull(memberDataCache.get(aliceIdGroup1))
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
        assertNull(lookupWithDefaults(bobIdGroup1))
    }

    @Test
    fun `Cache for one group and lookup for a different group`() {
        addToCacheWithDefaults()
        assertNull(lookupWithDefaults(aliceIdGroup2))
    }

    @Test
    fun `Cache and lookup for a multiple groups`() {
        addToCacheWithDefaults()
        addToCacheWithDefaults(
            aliceIdGroup2,
            memberData = memberData2
        )

        with(lookupWithDefaults()) {
            assertNotNull(this)
            assertEquals(memberData1, this)
        }

        with(lookupWithDefaults(aliceIdGroup2)) {
            assertNotNull(this)
            assertEquals(memberData2, this)
        }
    }

    @Test
    fun `Cache and lookup for multiple members in the same group`() {
        addToCacheWithDefaults()
        addToCacheWithDefaults(
            bobIdGroup1,
            memberData = memberData2
        )

        with(lookupWithDefaults()) {
            assertNotNull(this)
            assertEquals(memberData1, this)
        }

        with(lookupWithDefaults(bobIdGroup1)) {
            assertNotNull(this)
            assertEquals(memberData2, this)
        }
    }

    private fun lookupWithDefaults(
        holdingIdentity: HoldingIdentity = aliceIdGroup1
    ): MemberData? {
        return memberDataCache.get(holdingIdentity)
    }

    private fun addToCacheWithDefaults(
        holdingIdentity: HoldingIdentity = aliceIdGroup1,
        memberData: MemberData = memberData1
    ) {
        memberDataCache.put(holdingIdentity, memberData)
    }
}
