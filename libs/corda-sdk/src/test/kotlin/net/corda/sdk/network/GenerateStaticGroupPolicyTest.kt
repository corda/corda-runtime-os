package net.corda.sdk.network

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeType
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import net.corda.membership.rest.v1.types.request.MemberRegistrationRequest
import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

class GenerateStaticGroupPolicyTest {

    private val om = jacksonObjectMapper()

    @Test
    fun generatedOutputIsValidJson() {
        val ggp = GenerateStaticGroupPolicy()
        val returnValue = ggp.generateStaticGroupPolicy(GenerateStaticGroupPolicy.defaultMembers)
        val returnValueAsString = om.writeValueAsString(returnValue)
        assertThat(returnValueAsString).isNotBlank()
        assertThat(isValidJson(returnValueAsString)).isTrue()
        val parsed = om.readValue<Map<String, Any>>(returnValueAsString)
        assertThat(parsed["fileFormatVersion"]).isEqualTo(1)
    }

    @Test
    fun `when no members are passed, the members array is null`() {
        val ggp = GenerateStaticGroupPolicy()
        val returnValue = ggp.generateStaticGroupPolicy(null)
        val returnValueAsString = om.writeValueAsString(returnValue)
        val memberList = memberList(returnValueAsString)
        assertThat(memberList.nodeType).isEqualTo(JsonNodeType.NULL)
    }

    @Test
    fun `when default members are passed, default values are used`() {
        val ggp = GenerateStaticGroupPolicy()
        val returnValue = ggp.generateStaticGroupPolicy(GenerateStaticGroupPolicy.defaultMembers)
        val returnValueAsString = om.writeValueAsString(returnValue)
        val memberList = memberList(returnValueAsString)
        memberList.find { it["name"].asText() == "C=GB, L=London, O=Alice" }.apply {
            assertThat(this?.get("memberStatus")?.asText()).isEqualTo("ACTIVE")
            assertThat(this?.get("endpointUrl-1")?.asText()).isEqualTo("https://alice.corda5.r3.com:10000")
            assertThat(this?.get("endpointProtocol-1")?.asText()).isEqualTo("1")
        }
        assertThat(memberList.find { it["name"].asText() == "C=GB, L=London, O=Bob" }).isNotEmpty
        assertThat(memberList.find { it["name"].asText() == "C=GB, L=London, O=Charlie" }).isNotEmpty
    }

    @Test
    fun `passed member is in members array`() {
        val myMember = MemberRegistrationRequest(
            context = mapOf(
                "name" to "C=GB, L=London, O=Terry",
                "memberStatus" to "ACTIVE",
                "endpointUrl-1" to "https://terry.corda5.r3.com:10000",
                "endpointProtocol-1" to "1",
            )
        )
        val ggp = GenerateStaticGroupPolicy()
        val returnValue = ggp.generateStaticGroupPolicy(listOf(myMember))
        val returnValueAsString = om.writeValueAsString(returnValue)
        val memberList = memberList(returnValueAsString)
        assertNotNull(memberList.find { it["name"].asText() == "C=GB, L=London, O=Terry" })
        memberList.find { it["name"].asText() == "C=GB, L=London, O=Terry" }.apply {
            assertThat(this?.get("memberStatus")?.asText()).isEqualTo("ACTIVE")
            assertThat(this?.get("endpointUrl-1")?.asText()).isEqualTo("https://terry.corda5.r3.com:10000")
            assertThat(this?.get("endpointProtocol-1")?.asText()).isEqualTo("1")
        }
    }

    @Test
    fun `member list from x500 name populates`() {
        val ggp = GenerateStaticGroupPolicy()
        val member = MemberX500Name.parse("C=GB, L=London, O=Terry")
        val members = ggp.createMembersListFromListOfX500Names(listOf(member))
        val parsedMembers = om.readTree(om.writeValueAsString(members))
        parsedMembers.find { it["context"]["name"].asText() == member.toString() }.run {
            assertNotNull(this, "Member (as JsonNode) should not be null when parsed from string")
            assertThat(this["context"]["memberStatus"].asText()).isEqualTo("ACTIVE")
            assertThat(this["context"]["endpointUrl-1"].asText()).isEqualTo("https://member.corda5.r3.com:10000")
            assertThat(this["context"]["endpointProtocol-1"].asText()).isEqualTo("1")
        }
    }

    @Test
    fun `can generate a policy with a list of members`() {
        val myList = listOf(
            MemberX500Name.parse("C=GB, L=London, O=Dave"),
            MemberX500Name.parse("C=GB, L=London, O=Edith"),
            MemberX500Name.parse("C=GB, L=London, O=Fred")
        )
        val ggp = GenerateStaticGroupPolicy()
        val createdMembersBlock = ggp.createMembersListFromListOfX500Names(myList)
        val rawPolicyOutput = ggp.generateStaticGroupPolicy(createdMembersBlock)
        val returnValueAsString = om.writeValueAsString(rawPolicyOutput)
        val memberList = memberList(returnValueAsString)
        assertThat(memberList.find { it["name"].asText() == MemberX500Name.parse("C=GB, L=London, O=Dave").toString() }).isNotEmpty
        assertThat(memberList.find { it["name"].asText() == MemberX500Name.parse("C=GB, L=London, O=Edith").toString() }).isNotEmpty
        assertThat(memberList.find { it["name"].asText() == MemberX500Name.parse("C=GB, L=London, O=Fred").toString() }).isNotEmpty
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
