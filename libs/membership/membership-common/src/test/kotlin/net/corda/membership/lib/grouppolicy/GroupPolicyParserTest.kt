package net.corda.membership.lib.grouppolicy

import net.corda.membership.lib.grouppolicy.GroupPolicyConstants.PolicyValues.Root.MGM_DEFAULT_GROUP_ID
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GroupPolicyParserTest {

    @Test
    fun `group policy parser can extract group id`() {
        val groupId = "fc651419-b507-4c3f-845d-7286c5f03b15"
        val groupPolicyJson = """{ "groupId" : "$groupId"}"""

        val actualGroupId = GroupPolicyParser.getOrCreateGroupId(groupPolicyJson)

        assertThat(actualGroupId).isEqualTo(groupId)
    }

    @Test
    fun `group policy parser fails to extract group id`() {
        val groupPolicyJson = "{}"

        assertThrows<CordaRuntimeException> {
            GroupPolicyParser.getOrCreateGroupId(groupPolicyJson)
        }
    }

    @Test
    fun `group policy parser generates group id when not defined for MGM`() {
        val groupPolicyJson = """{ "groupId" : "$MGM_DEFAULT_GROUP_ID"}"""

        val groupId = GroupPolicyParser.getOrCreateGroupId(groupPolicyJson)

        assertThat(groupId).isNotEqualTo(MGM_DEFAULT_GROUP_ID)
    }

    @Test
    fun `group policy parser fails to extract group id when json is invalid`() {
        val groupPolicyJson = """{ "groupIds" : "$MGM_DEFAULT_GROUP_ID"}"""

        assertThrows<CordaRuntimeException> {
            GroupPolicyParser.getOrCreateGroupId(groupPolicyJson)
        }
    }
}