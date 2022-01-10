package net.corda.membership.staticmemberlist

import net.corda.membership.impl.GroupPolicyImpl
import net.corda.membership.staticmemberlist.StaticMemberTemplateExtension.Companion.mgmKeyAlias
import net.corda.membership.staticmemberlist.StaticMemberTemplateExtension.Companion.staticMembers
import net.corda.membership.staticmemberlist.StaticMemberTemplateExtension.Companion.staticMgm
import net.corda.membership.staticmemberlist.StaticMemberTemplateExtension.Companion.staticNetwork
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class StaticMemberTemplateExtensionTest {
    companion object {
        private const val MGM_KEY_ALIAS = "mgm-alias"
        private const val ALICE = "C=GB, L=London, O=Alice"
        private const val BOB = "C=GB, L=London, O=Bob"
        private const val CHARLIE = "C=GB, L=London, O=Charlie"

        private val groupPolicyWithStaticNetwork = GroupPolicyImpl(
            mapOf(
                "staticNetwork" to mapOf(
                    "mgm" to mapOf(
                        "keyAlias" to MGM_KEY_ALIAS
                    ),
                    "members" to listOf(
                        mapOf(
                            "name" to ALICE,
                            "keyAlias" to "alice-alias",
                            "rotatedKeyAlias-1" to "alice-historic-alias-1",
                            "memberStatus" to "ACTIVE",
                            "endpointUrl-1" to "https://alice.corda5.r3.com:10000",
                            "endpointProtocol-1" to "1"
                        ),
                        mapOf(
                            "name" to BOB,
                            "keyAlias" to "bob-alias",
                            "rotatedKeyAlias-1" to "bob-historic-alias-1",
                            "rotatedKeyAlias-2" to "bob-historic-alias-2",
                            "memberStatus" to "ACTIVE",
                            "endpointUrl-1" to "https://bob.corda5.r3.com:10000",
                            "endpointProtocol-1" to "1"
                        ),
                        mapOf(
                            "name" to CHARLIE,
                            "keyAlias" to "charlie-alias",
                            "memberStatus" to "SUSPENDED",
                            "endpointUrl-1" to "https://charlie.corda5.r3.com:10000",
                            "endpointProtocol-1" to "1",
                            "endpointUrl-2" to "https://charlie-dr.corda5.r3.com:10001",
                            "endpointProtocol-2" to "1"
                        )
                    )
                )
            )
        )

        private val memberNames = listOf(ALICE, BOB, CHARLIE)

        private val groupPolicyWithoutStaticNetwork = GroupPolicyImpl(emptyMap())

        private val groupPolicyWithInvalidStructure = GroupPolicyImpl(
            mapOf(
                "staticNetwork" to mapOf(
                    "members" to mapOf("key" to "value")
                )
            )
        )
    }

    @Test
    fun `static network parameters are not empty when group policy file with static network is used`() {
        assertNotEquals(0, groupPolicyWithStaticNetwork.staticNetwork.size)
        val staticMgmSize = groupPolicyWithStaticNetwork.staticMgm.size
        val staticMembersSize = groupPolicyWithStaticNetwork.staticMembers.size
        assertNotEquals(0, staticMgmSize)
        assertNotEquals(0, staticMembersSize)
        assertEquals(1, staticMgmSize)
        assertEquals(3, staticMembersSize)
    }

    @Test
    fun `static network parameters are empty when group policy file without static network is used`() {
        assertEquals(0, groupPolicyWithoutStaticNetwork.staticNetwork.size)
        assertEquals(0, groupPolicyWithoutStaticNetwork.staticMgm.size)
        assertEquals(0, groupPolicyWithoutStaticNetwork.staticMembers.size)
    }

    @Test
    fun `static member list parsing fails when invalid structure is used`() {
        val ex = assertFailsWith<ClassCastException> { groupPolicyWithInvalidStructure.staticMembers }
        assertEquals("Casting failed for static members from group policy JSON.", ex.message)
    }

    @Test
    fun `retrieving static MGM key alias`() {
        val keyAlias = groupPolicyWithStaticNetwork.mgmKeyAlias
        assertNotNull(keyAlias)
        assertEquals(MGM_KEY_ALIAS, keyAlias)
    }

    @Test
    fun `retrieving member list`() {
        val names = groupPolicyWithStaticNetwork.staticMembers.map { it.get("name") }
        assertEquals(3, names.size)
        assertEquals(memberNames, names)
    }
}