package net.corda.chunking.db.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

import java.util.UUID

internal class GroupPolicyParserTest {
    @Test
    fun parse() {
        val groupId = UUID.randomUUID().toString()
        val groupPolicyJson = "{ groupId : $groupId}"
        val groupPolicy = GroupPolicyParser.parse(groupPolicyJson)
        assertThat(groupPolicy["groupId"]?.toString()).isEqualTo(groupId)
    }
}
