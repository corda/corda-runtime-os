package net.corda.cli.commands.network

import net.corda.cli.commands.network.utils.HoldingIdentityUtils
import net.corda.cli.commands.network.utils.inferCpiName
import net.corda.e2etest.utilities.DEFAULT_CLUSTER
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRestResource
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.rest.v1.MGMRestResource
import net.corda.membership.rest.v1.types.request.PreAuthTokenRequest
import net.corda.sdk.network.MgmGeneratePreAuth
import net.corda.sdk.packaging.CpiUploader
import net.corda.sdk.rest.RestClientUtils
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

class OnboardMemberTest {
    companion object {
        private const val CPB_FILE = "test-cordapp.cpb"

        private val targetUrl = "--target=${DEFAULT_CLUSTER.rest.uri}"
        private val user = "--user=${DEFAULT_CLUSTER.rest.user}"
        private val password = "--password=${DEFAULT_CLUSTER.rest.password}"
        private const val INSECURE = "--insecure=true"

        private val mgmName = MemberX500Name.parse("O=MGM-${UUID.randomUUID()}, L=London, C=GB")
        private fun memberName() = MemberX500Name.parse("O=Member-${UUID.randomUUID()}, L=London, C=GB")

        private lateinit var outputStub: OutputStub
        private lateinit var command: OnboardMgm
        private lateinit var cpbLocation: String
        private lateinit var defaulGroupPolicyLocation: String

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
        }
    }

    @BeforeEach
    fun beforeEach() {
        outputStub = OutputStub()
    }

    @Test
    fun `onboarding member with CPB and group policy succeeds`() {
        val member = memberName()
        val cpbLocation = this::class.java.classLoader.getResource("OnboardMemberTest/single-use.cpb")!!.path

        CommandLine(OnboardMember()).execute(
            member.toString(),
            "--cpb-file=$cpbLocation",
            "--group-policy-file=$defaulGroupPolicyLocation",
            targetUrl,
            user,
            password,
            INSECURE,
            "--wait",
        )

        outputStub.lookup(member)
        assertThat(outputStub.getAllPartyNames().contains(member)).isTrue
    }

    @Test
    fun `onboarding member with CPI hash succeeds`() {
        CommandLine(OnboardMember()).execute(
            memberName().toString(),
            "--cpb-file=$cpbLocation",
            targetUrl,
            user,
            password,
            INSECURE,
            "--wait",
        )
        val member = memberName()
        val cpiHash = command.getExistingCpiHash()

        CommandLine(OnboardMember()).execute(
            member.toString(),
            "--cpi-hash=$cpiHash",
            targetUrl,
            user,
            password,
            INSECURE,
            "--wait",
        )

        outputStub.lookup(member)
        assertThat(outputStub.getAllPartyNames().contains(member)).isTrue
    }

    @Test
    fun `onboarding member with provided gateway URLs sets correct registration context`() {
        val member = memberName()
        val gatewayUrl0 = "https://localhost:8080"
        val gatewayUrl1 = "https://localhost:8081"

        CommandLine(OnboardMember()).execute(
            member.toString(),
            "--cpb-file=$cpbLocation",
            "--p2p-gateway-url=$gatewayUrl0",
            "--p2p-gateway-url=$gatewayUrl1",
            targetUrl,
            user,
            password,
            INSECURE,
            "--wait",
        )

        outputStub.lookup(member)
        val output = outputStub.printedOutput?.get(0)?.get("memberContext")
        assertThat(output).isNotNull
        assertThat(output!!.get("corda.endpoints.0.connectionURL")?.asText()).isEqualTo(gatewayUrl0)
        assertThat(output.get("corda.endpoints.1.connectionURL")?.asText()).isEqualTo(gatewayUrl1)
    }

    @Test
    fun `onboarding member with notary role succeeds`() {
        val member = memberName()
        val notaryServiceName = "O=Notary, L=London, C=GB"

        CommandLine(OnboardMember()).execute(
            member.toString(),
            "--cpb-file=$cpbLocation",
            "--role=NOTARY",
            "--set=${MemberInfoExtension.NOTARY_SERVICE_NAME}=$notaryServiceName",
            targetUrl,
            user,
            password,
            INSECURE,
            "--wait",
        )

        outputStub.lookup(member)
        assertThat(outputStub.getAllPartyNames().contains(member)).isTrue
    }

    @Test
    fun `onboarding member with pre-auth token succeeds`() {
        val member = memberName()
        val preAuthToken = command.createPreAuthToken(member.toString())

        CommandLine(OnboardMember()).execute(
            member.toString(),
            "--cpb-file=$cpbLocation",
            "--pre-auth-token=$preAuthToken",
            targetUrl,
            user,
            password,
            INSECURE,
            "--wait",
        )

        outputStub.lookup(member)
        assertThat(outputStub.getAllPartyNames().contains(member)).isTrue
    }

    @Test
    fun `can onboard member to a group with custom metadata`() {
        val member = memberName()
        val customKey = "ext.member.key.0"
        val customValue = "value0"

        CommandLine(OnboardMember()).execute(
            member.toString(),
            "--cpb-file=$cpbLocation",
            "--set=$customKey=$customValue",
            targetUrl,
            user,
            password,
            INSECURE,
            "--wait",
        )

        outputStub.lookup(member)
        val output = outputStub.printedOutput?.get(0)?.get("memberContext")
        assertThat(output).isNotNull
        assertThat(output!!.get(customKey)?.asText()).isEqualTo(customValue)
    }

    private fun OnboardMgm.getExistingCpiHash(): String {
        val cpiName = inferCpiName(File(cpbLocation), File(defaulGroupPolicyLocation))
        val restClient = RestClientUtils.createRestClient(
            CpiUploadRestResource::class,
            insecure = insecure,
            minimumServerProtocolVersion = minimumServerProtocolVersion,
            username = username,
            password = password,
            targetUrl = targetUrl
        )
        val cpisFromCluster = CpiUploader().getAllCpis(restClient = restClient, wait = waitDurationSeconds.seconds).cpis
        return cpisFromCluster.first { it.id.cpiName == cpiName }.cpiFileChecksum
    }

    private fun OnboardMgm.createPreAuthToken(member: String): String {
        val holdingIdentity = HoldingIdentityUtils.getHoldingIdentity(
            null,
            mgmName,
            null,
        )
        val restClient = RestClientUtils.createRestClient(
            MGMRestResource::class,
            insecure = insecure,
            minimumServerProtocolVersion = minimumServerProtocolVersion,
            username = username,
            password = password,
            targetUrl = targetUrl
        )
        return MgmGeneratePreAuth().generatePreAuthToken(
            restClient = restClient,
            holdingIdentityShortHash = holdingIdentity,
            request = PreAuthTokenRequest(member),
            wait = waitDurationSeconds.seconds
        ).id
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
