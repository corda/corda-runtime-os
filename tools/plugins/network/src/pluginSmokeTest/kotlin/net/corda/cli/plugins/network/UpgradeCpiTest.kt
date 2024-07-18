package net.corda.cli.plugins.network

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.e2etest.utilities.DEFAULT_CLUSTER
import net.corda.restclient.CordaRestClient
import net.corda.restclient.generated.models.RestMemberInfo
import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import picocli.CommandLine
import java.io.File
import java.util.UUID

class UpgradeCpiTest {
    companion object {
        private const val CPB_FILE = "test-cordapp.cpb"

        private val targetUrl = "--target=${DEFAULT_CLUSTER.rest.uri}"
        private val user = "--user=${DEFAULT_CLUSTER.rest.user}"
        private val password = "--password=${DEFAULT_CLUSTER.rest.password}"
        private const val INSECURE = "--insecure=true"

        private fun getMemberName(name: String = "Member") = MemberX500Name.parse("O=$name-${UUID.randomUUID()}, L=London, C=GB")

        private val mgmName = getMemberName("MGM")
        private val memberNameAlice = getMemberName("Alice")
        private val memberNameBob = getMemberName("Bob")
        private val memberNameCharlie = getMemberName("Charlie")

        private lateinit var outputStub: OutputStub
        private val cpbLocation: String = this::class.java.classLoader.getResource(CPB_FILE)!!.path
        private val defaultGroupPolicyLocation: String = "${System.getProperty("user.home")}/.corda/gp/groupPolicy.json"
        private val restClient = CordaRestClient.createHttpClient(
            baseUrl = DEFAULT_CLUSTER.rest.uri,
            username = DEFAULT_CLUSTER.rest.user,
            password = DEFAULT_CLUSTER.rest.password,
            insecure = true,
        )

        private lateinit var groupId: String
        private lateinit var mgmHoldingId: String

        @BeforeAll
        @JvmStatic
        fun setup() {
            onboardMgm()

            // TODO is it needed?
            groupId = ObjectMapper().readTree(File(defaultGroupPolicyLocation).readText()).get("groupId").asText()

            onboardMember(memberNameAlice)
            onboardMember(memberNameBob)

            assertThat(getGroupMembers().size).isEqualTo(3)
        }

        private fun onboardMgm() {
            CommandLine(OnboardMgm()).execute(mgmName.toString(), targetUrl, user, password, INSECURE)
            mgmHoldingId = getHoldingIdForMember(mgmName)
            assertThat(getGroupMembersNames()).containsExactly(mgmName.toString())
        }

        private fun getGroupMembers(): List<RestMemberInfo> =
            restClient.memberLookupClient.getMembersHoldingidentityshorthash(mgmHoldingId).members

        private fun getGroupMembersNames(): List<String> = getGroupMembers().map { it.memberContext["corda.name"]!! }

        private fun getHoldingIdForMember(memberName: MemberX500Name): String =
            restClient.virtualNodeClient.getVirtualnode().virtualNodes
                .first { it.holdingIdentity.x500Name == memberName.toString() }
                .holdingIdentity.shortHash

        private fun onboardMember(memberName: MemberX500Name) {
            CommandLine(OnboardMember()).execute(
                memberName.toString(),
                "--cpb-file=$cpbLocation", // TODO: prepare the CPI file
                "--group-policy-file=$defaultGroupPolicyLocation",
                targetUrl,
                user,
                password,
                INSECURE,
                "--wait",
            )
            assertThat(getGroupMembersNames()).contains(memberName.toString())
        }
    }

    @BeforeEach
    fun beforeEach() {
        outputStub = OutputStub()
    }

    @Test
    fun `foo`() {
        println("Foo")
    }
}
