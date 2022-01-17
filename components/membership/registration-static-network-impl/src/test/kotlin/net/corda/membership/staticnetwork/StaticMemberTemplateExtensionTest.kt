package net.corda.membership.staticnetwork

import net.corda.membership.staticnetwork.StaticMemberTemplateExtension.Companion.mgmKeyAlias
import net.corda.membership.staticnetwork.StaticMemberTemplateExtension.Companion.staticMembers
import net.corda.membership.staticnetwork.StaticMemberTemplateExtension.Companion.staticMgm
import net.corda.membership.staticnetwork.StaticMemberTemplateExtension.Companion.staticNetwork
import net.corda.membership.staticnetwork.TestUtils.Companion.MGM_KEY_ALIAS
import net.corda.membership.staticnetwork.TestUtils.Companion.aliceName
import net.corda.membership.staticnetwork.TestUtils.Companion.bobName
import net.corda.membership.staticnetwork.TestUtils.Companion.charlieName
import net.corda.membership.staticnetwork.TestUtils.Companion.groupPolicyWithCastingFailure
import net.corda.membership.staticnetwork.TestUtils.Companion.groupPolicyWithStaticNetwork
import net.corda.membership.staticnetwork.TestUtils.Companion.groupPolicyWithoutStaticNetwork
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class StaticMemberTemplateExtensionTest {
    companion object {
        private val memberNames = listOf(aliceName.toString(), bobName.toString(), charlieName.toString())
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
        val ex = assertFailsWith<ClassCastException> { groupPolicyWithCastingFailure.staticMembers }
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