package net.corda.sdk.network

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GenerateGroupPolicyTest {

    private val om = jacksonObjectMapper()
    private val defaultMembers by lazy {
        listOf(
            mapOf(
                "name" to "C=GB, L=London, O=Alice",
                "memberStatus" to "ACTIVE",
                "endpointUrl-1" to "https://alice.corda5.r3.com:10000",
                "endpointProtocol-1" to 1,
            ),
            mapOf(
                "name" to "C=GB, L=London, O=Bob",
                "memberStatus" to "ACTIVE",
                "endpointUrl-1" to "https://bob.corda5.r3.com:10000",
                "endpointProtocol-1" to 1,
            ),
            mapOf(
                "name" to "C=GB, L=London, O=Charlie",
                "memberStatus" to "SUSPENDED",
                "endpointUrl-1" to "https://charlie.corda5.r3.com:10000",
                "endpointProtocol-1" to 1,
                "endpointUrl-2" to "https://charlie-dr.corda5.r3.com:10001",
                "endpointProtocol-2" to 1,
            ),
        )
    }

    @Test
    fun generatedOutputIsValidJson() {
        val ggp = GenerateGroupPolicy()
        val returnValue = ggp.generateStaticGroupPolicy(defaultMembers)
        val returnValueAsString = om.writeValueAsString(returnValue)
        assertNotEquals(0, returnValueAsString.length)
        assertTrue(isValidJson(returnValueAsString))
        val parsed = om.readValue<Map<String, Any>>(returnValueAsString)
        assertEquals(1, parsed["fileFormatVersion"])
    }

    @Test
    fun `when no members are passed, the members array is null`() {
        val ggp = GenerateGroupPolicy()
        val returnValue = ggp.generateStaticGroupPolicy(null)
        val returnValueAsString = om.writeValueAsString(returnValue)
        val memberList = memberList(returnValueAsString)
        assertTrue(memberList.isNull)
    }

    @Test
    fun `passed member is in members array`() {
        val myMember = mapOf(
            "name" to "C=GB, L=London, O=Terry",
            "memberStatus" to "ACTIVE",
            "endpointUrl-1" to "https://terry.corda5.r3.com:10000",
            "endpointProtocol-1" to 1,
        )
        val ggp = GenerateGroupPolicy()
        val returnValue = ggp.generateStaticGroupPolicy(listOf(myMember))
        val returnValueAsString = om.writeValueAsString(returnValue)
        val memberList = memberList(returnValueAsString)
        assertNotNull(memberList.find { it["name"].asText() == "C=GB, L=London, O=Terry" })
        memberList.find { it["name"].asText() == "C=GB, L=London, O=Terry" }.apply {
            assertEquals("ACTIVE", this?.get("memberStatus")?.asText())
            assertEquals("https://terry.corda5.r3.com:10000", this?.get("endpointUrl-1")?.asText())
            assertEquals("1", this?.get("endpointProtocol-1")?.asText())
        }
    }

    @Test
    fun `member list from string populates`() {
        val ggp = GenerateGroupPolicy()
        val members = ggp.createMembersListFromListOfX500Strings(listOf("C=GB, L=London, O=Terry"))
        val parsedMembers = om.readTree(om.writeValueAsString(members))
        parsedMembers.find { it["name"].asText() == "C=GB, L=London, O=Terry" }.apply {
            assertEquals("ACTIVE", this?.get("memberStatus")?.asText())
            assertEquals("https://member.corda5.r3.com:10000", this?.get("endpointUrl-1")?.asText())
            assertEquals("1", this?.get("endpointProtocol-1")?.asText())
        }
    }

    @Test
    fun `can generate a policy with a list of member name strings`() {
        val myList = listOf(
            "C=GB, L=London, O=Dave",
            "C=GB, L=London, O=Edith",
            "C=GB, L=London, O=Fred"
        )
        val ggp = GenerateGroupPolicy()
        val createdMembersBlock = ggp.createMembersListFromListOfX500Strings(myList)
        val rawPolicyOutput = ggp.generateStaticGroupPolicy(createdMembersBlock)
        val returnValueAsString = om.writeValueAsString(rawPolicyOutput)
        val memberList = memberList(returnValueAsString)
        assertNotNull(memberList.find { it["name"].asText() == "C=GB, L=London, O=Dave" })
        assertNotNull(memberList.find { it["name"].asText() == "C=GB, L=London, O=Edith" })
        assertNotNull(memberList.find { it["name"].asText() == "C=GB, L=London, O=Fred" })
    }

    /**
     * Extracts the members from the policy
     */
    private fun memberList(output: String): JsonNode {
        return om.readTree(output)["protocolParameters"]["staticNetwork"]["members"]
    }

    /**
     * Checks that the [content] String is a valid JSON.
     */
    private fun isValidJson(content: String): Boolean {
        return try {
            om.readTree(content)
            true
        } catch (e: Exception) {
            false
        }
    }
}
