package net.corda.membership.impl.read.cache

import net.corda.membership.read.MembershipGroupReader
import net.corda.v5.membership.identity.MemberX500Name
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class GroupReaderCacheImplTest {

    private lateinit var groupReaderCache: GroupReaderCache

    private val groupId1 = "GROUP_ID1"
    private val groupId2 = "GROUP_ID2"
    private val lookUpMemberName = MemberX500Name("Alice", "London", "GB")
    private val memberName1 = MemberX500Name("Bob", "London", "GB")
    private val groupReader1 = mock<MembershipGroupReader>()
    private val groupReader2 = mock<MembershipGroupReader>()

    @BeforeEach
    fun setUp() {
        groupReaderCache = GroupReaderCache.Impl()
    }

    @Test
    fun `Get group reader before any data is cached`() {
        assertNull(groupReaderCache.get(groupId1, lookUpMemberName))
    }

    @Test
    fun `Add group reader to cache then read same group reader from cache`() {
        addToCacheWithDefaults()
        with(lookupWithDefaults()) {
            assertNotNull(this)
            assertEquals(groupReader1, this)
        }
    }

    @Test
    fun `Cache for one member and lookup for a different member`() {
        addToCacheWithDefaults(
            lookUpMember = memberName1
        )
        assertNull(lookupWithDefaults())
    }

    @Test
    fun `Cache for one group and lookup for a different group`() {
        addToCacheWithDefaults()
        assertNull(lookupWithDefaults(groupId = groupId2))
    }

    @Test
    fun `Cache and lookup for a multiple groups`() {
        addToCacheWithDefaults()
        addToCacheWithDefaults(
            groupId = groupId2,
            groupReader = groupReader2
        )

        with(lookupWithDefaults()) {
            assertNotNull(this)
            assertEquals(groupReader1, this)
        }

        with(lookupWithDefaults(groupId = groupId2)) {
            assertNotNull(this)
            assertEquals(groupReader2, this)
        }
    }

    @Test
    fun `Cache and lookup for multiple members in the same group`() {
        addToCacheWithDefaults()
        addToCacheWithDefaults(
            lookUpMember = memberName1,
            groupReader = groupReader2
        )

        with(lookupWithDefaults()) {
            assertNotNull(this)
            assertEquals(groupReader1, this)
        }

        with(lookupWithDefaults(lookUpMember = memberName1)) {
            assertNotNull(this)
            assertEquals(groupReader2, this)
        }
    }

    private fun lookupWithDefaults(
        groupId: String = groupId1,
        lookUpMember: MemberX500Name = lookUpMemberName,
    ): MembershipGroupReader? {
        return groupReaderCache.get(groupId, lookUpMember)
    }

    private fun addToCacheWithDefaults(
        groupId: String = groupId1,
        lookUpMember: MemberX500Name = lookUpMemberName,
        groupReader: MembershipGroupReader = groupReader1
    ) {
        groupReaderCache.put(groupId, lookUpMember, groupReader)
    }
}
