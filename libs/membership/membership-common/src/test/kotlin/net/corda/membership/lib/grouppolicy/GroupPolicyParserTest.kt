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

        val actualGroupId = GroupPolicyParser.groupIdFromJson(groupPolicyJson)

        assertThat(actualGroupId).isEqualTo(groupId)
    }

    @Test
    fun `group policy parser fails to extract group id`() {
        val groupPolicyJson = "{}"

        assertThrows<CordaRuntimeException> {
            GroupPolicyParser.groupIdFromJson(groupPolicyJson)
        }
    }

    @Test
    fun `group policy parser fails to extract group id when json is invalid`() {
        val groupPolicyJson = """{ "groupIds" : "$MGM_DEFAULT_GROUP_ID"}"""

        assertThrows<CordaRuntimeException> {
            GroupPolicyParser.groupIdFromJson(groupPolicyJson)
        }
    }

    @Test
    fun `group policy parser can extract file format version`() {
        val fileFormatVersion = 1
        val groupPolicyJson = """{ "fileFormatVersion" : $fileFormatVersion}"""

        val parsedFileFormatVersion = GroupPolicyParser.getFileFormatVersion(groupPolicyJson)

        assertThat(parsedFileFormatVersion).isEqualTo(fileFormatVersion)
    }

    @Test
    fun `group policy parser fails to extract file format version`() {
        val groupPolicyJson = "{}"

        assertThrows<CordaRuntimeException> {
            GroupPolicyParser.getFileFormatVersion(groupPolicyJson)
        }
    }

    @Test
    fun `group policy parser fails to extract file format version if json can't be parsed`() {
        val fileFormatVersion = 1
        val groupPolicyJson = """{[{ "fileFormatVersion" : $fileFormatVersion}"""

        assertThrows<CordaRuntimeException> {
            GroupPolicyParser.getFileFormatVersion(groupPolicyJson)
        }
    }

    @Test
    fun `group policy parser fails to extract file format version if version isn't an int`() {
        val groupPolicyJson = """{[{ "fileFormatVersion" : "BAD_INT"}"""

        assertThrows<CordaRuntimeException> {
            GroupPolicyParser.getFileFormatVersion(groupPolicyJson)
        }
    }
}