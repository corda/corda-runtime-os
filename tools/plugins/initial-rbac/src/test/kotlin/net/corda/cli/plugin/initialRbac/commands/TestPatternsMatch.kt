package net.corda.cli.plugin.initialRbac.commands

import net.corda.cli.plugin.initialRbac.commands.RoleCreationUtils.CLIENT_REQ_REGEX
import net.corda.cli.plugin.initialRbac.commands.RoleCreationUtils.FLOW_NAME_REGEX
import net.corda.cli.plugin.initialRbac.commands.RoleCreationUtils.USER_REGEX
import net.corda.cli.plugin.initialRbac.commands.RoleCreationUtils.USER_URL_REGEX
import net.corda.cli.plugin.initialRbac.commands.RoleCreationUtils.UUID_REGEX
import net.corda.cli.plugin.initialRbac.commands.RoleCreationUtils.VNODE_SHORT_HASH_REGEX
import net.corda.cli.plugin.initialRbac.commands.RoleCreationUtils.wildcardMatch
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.regex.Pattern
import java.util.regex.Pattern.CASE_INSENSITIVE

class TestPatternsMatch {

    @Test
    fun testUUID() {
        val validUUIDString = UUID.randomUUID().toString()

        assertTrue(wildcardMatch(validUUIDString, UUID_REGEX))
        assertFalse(wildcardMatch("invalid", UUID_REGEX))
    }

    @Test
    fun testVNodeShortHash() {
        val validShortHashId = "ABCDEF123456"

        assertTrue(wildcardMatch(validShortHashId, VNODE_SHORT_HASH_REGEX))
        assertFalse(wildcardMatch("invalid", VNODE_SHORT_HASH_REGEX))
    }

    @Test
    fun testUser() {
        listOf("joe.bloggs@company_1.com", "plainUser").forEach { validUserName ->
            assertTrue(wildcardMatch(validUserName, USER_REGEX)) { "Failed for $validUserName" }
        }
        assertFalse(wildcardMatch("joe/bloggs", USER_REGEX))
        assertFalse(wildcardMatch("0", USER_REGEX))
    }

    @Test
    fun testUserInURL() {
        listOf("joe.bloggs%40company_1.com", "plainUser").forEach { validUserName ->
            assertTrue(wildcardMatch(validUserName, USER_URL_REGEX)) { "Failed for $validUserName" }
        }

        assertFalse(wildcardMatch("joe/bloggs", USER_URL_REGEX))
        assertFalse(wildcardMatch("0", USER_URL_REGEX))
    }

    @Test
    fun testClientRequestId() {
        val validClientRequestId = "My-Request_Id.1"

        assertTrue(wildcardMatch(validClientRequestId, CLIENT_REQ_REGEX))
        assertFalse(wildcardMatch("client/request", CLIENT_REQ_REGEX))
        assertFalse(wildcardMatch("#client@request", CLIENT_REQ_REGEX))
    }

    @Test
    fun testFlowName() {
        val validFlowName = "com.company.MyFlow_1$2"

        assertTrue(wildcardMatch(validFlowName, FLOW_NAME_REGEX))
        assertFalse(wildcardMatch("flow/name", FLOW_NAME_REGEX))
        assertFalse(wildcardMatch("#flow@name", FLOW_NAME_REGEX))
    }
}