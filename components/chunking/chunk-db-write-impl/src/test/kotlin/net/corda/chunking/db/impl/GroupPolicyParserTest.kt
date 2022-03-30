package net.corda.chunking.db.impl

import net.corda.chunking.db.impl.validation.GroupPolicyParser
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

import java.util.UUID

internal class GroupPolicyParserTest {
    @Test
    fun `group policy parser can extract group id`() {
        val groupId = UUID.randomUUID().toString()
        val groupPolicyJson = """{ "groupId" : "$groupId"}"""
        val actualGroupId = GroupPolicyParser.groupId(groupPolicyJson)
        assertThat(actualGroupId).isEqualTo(groupId)
    }

    @Test
    fun `group policy parser fails to extract group id`() {
        val groupId = UUID.randomUUID().toString()
        val groupPolicyJson = """{ "nothing" : "$groupId"}"""
        assertThrows<CordaRuntimeException> {
            GroupPolicyParser.groupId(groupPolicyJson)
        }
    }
}
