package net.corda.cli.plugin.initialRbac.commands

import net.corda.cli.plugin.initialRbac.commands.RoleCreationUtils.USER_REGEX
import net.corda.cli.plugin.initialRbac.commands.RoleCreationUtils.UUID_REGEX
import net.corda.cli.plugin.initialRbac.commands.RoleCreationUtils.VNODE_SHORT_HASH_REGEX
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class TestPatternsMatch {

    companion object {
        private fun wildcardMatch(input: String, regex: String): Boolean {
            return input.matches(regex.toRegex(RegexOption.IGNORE_CASE))
        }
    }

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
        val validUserName = "joe.bloggs@company_1.com"

        assertTrue(wildcardMatch(validUserName, USER_REGEX))
        assertFalse(wildcardMatch("joe/bloggs", VNODE_SHORT_HASH_REGEX))
        assertFalse(wildcardMatch("0", VNODE_SHORT_HASH_REGEX))
    }
}