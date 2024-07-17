package net.corda.cli.plugins.network

import net.corda.cli.plugins.network.utils.HoldingIdentityUtils
import net.corda.cli.plugins.network.utils.inferCpiName
import net.corda.e2etest.utilities.DEFAULT_CLUSTER
import net.corda.restclient.CordaRestClient
import net.corda.restclient.generated.models.PreAuthTokenRequest
import net.corda.sdk.network.MgmGeneratePreAuth
import net.corda.sdk.network.VirtualNode
import net.corda.sdk.packaging.CpiUploader
import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import picocli.CommandLine
import java.io.File
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

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

//        val cpbLocation = this::class.java.classLoader.getResource("OnboardMemberTest/single-use.cpb")!!.path

        private lateinit var outputStub: OutputStub
        private lateinit var command: OnboardMgm
        private lateinit var cpbLocation: String
        private lateinit var defaulGroupPolicyLocation: String
        private val restClient = CordaRestClient.createHttpClient(
            baseUrl = DEFAULT_CLUSTER.rest.uri,
            username = DEFAULT_CLUSTER.rest.user,
            password = DEFAULT_CLUSTER.rest.password,
            insecure = true,
        )

        @BeforeAll
        @JvmStatic
        fun setup() {
            command = OnboardMgm()
            outputStub = OutputStub()
            cpbLocation = this::class.java.classLoader.getResource(CPB_FILE)!!.path
            defaulGroupPolicyLocation = "${System.getProperty("user.home")}/.corda/gp/groupPolicy.json"

            CommandLine(command).execute(
                mgmName.toString(),
                targetUrl,
                user,
                password,
                INSECURE,
            )
            CommandLine(MemberLookup(outputStub)).execute(
                "-n=$mgmName",
                targetUrl,
                user,
                password,
                INSECURE,
            )
            assertEquals(mgmName, outputStub.getFirstPartyName())

            onboardMember(memberNameAlice)
            onboardMember(memberNameBob)
        }

        private fun onboardMember(memberName: MemberX500Name) {
            CommandLine(OnboardMember()).execute(
                memberName.toString(),
                "--cpb-file=$cpbLocation",
                "--group-policy-file=$defaulGroupPolicyLocation",
                targetUrl,
                user,
                password,
                INSECURE,
                "--wait",
            )

            outputStub.lookup(memberName)
            assertThat(outputStub.getAllPartyNames().contains(memberName)).isTrue
        }

        private fun OutputStub.lookup(memberName: MemberX500Name) {
            CommandLine(MemberLookup(this)).execute(
                "-n=$mgmName",
                "-o=${memberName.organization}",
                targetUrl,
                user,
                password,
                INSECURE,
            )
        }
    }

    @BeforeEach
    fun beforeEach() {
        outputStub = OutputStub()
    }

    @Test
    fun `foo`() {
        println("Foo")
        val virtualNodes = VirtualNode(restClient).getAllVirtualNodes()
        println("Total virtual nodes: ${virtualNodes.virtualNodes.size}")
        virtualNodes.virtualNodes.filter {
            it.holdingIdentity.x500Name in listOf(
                memberNameAlice.toString(),
                memberNameBob.toString(),
                memberNameCharlie.toString(),
                mgmName.toString(),
            )
        }.forEach {
            println(it.holdingIdentity.toString() + " " + it.cpiIdentifier)
        }

        val mgmHoldingId = virtualNodes.virtualNodes.first { it.holdingIdentity.x500Name == mgmName.toString() }.holdingIdentity.shortHash
        val members = restClient.memberLookupClient.getMembersHoldingidentityshorthash(mgmHoldingId)

        println("Found members for mgm with holding id $mgmHoldingId: ${members.members.size}")
        println(members.members.map { it.memberContext["corda.name"] }.joinToString("\n"))
        Unit
    }

    private fun OnboardMgm.getExistingCpiHash(): String {
        val cpiName = inferCpiName(File(cpbLocation), File(defaulGroupPolicyLocation))
        val cpisFromCluster = CpiUploader(restClient).getAllCpis(wait = waitDurationSeconds.seconds).cpis
        return cpisFromCluster.first { it.id.cpiName == cpiName }.cpiFileChecksum
    }

    private fun OnboardMgm.createPreAuthToken(member: String): String {
        val holdingIdentity = HoldingIdentityUtils.getHoldingIdentity(
            null,
            mgmName,
            null,
        )
        return MgmGeneratePreAuth(restClient).generatePreAuthToken(
            holdingIdentityShortHash = holdingIdentity,
            request = PreAuthTokenRequest(member),
        ).id
    }
}
