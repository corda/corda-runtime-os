@file:Suppress("DEPRECATION")

package net.corda.cli.plugins.network

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.cli.plugins.common.RestClientUtils.createRestClient
import net.corda.cli.plugins.network.utils.HoldingIdentityUtils
import net.corda.e2etest.utilities.DEFAULT_CLUSTER
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRestResource
import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import picocli.CommandLine
import java.io.File
import java.util.UUID

class OnboardMgmTest {
    companion object {
        private lateinit var outputStub: OutputStub

        private val targetUrl = "--target=${DEFAULT_CLUSTER.rest.uri}"
        private val user = "--user=${DEFAULT_CLUSTER.rest.user}"
        private val password = "--password=${DEFAULT_CLUSTER.rest.password}"
        private const val INSECURE = "--insecure=true"

        private fun mgmName() = MemberX500Name.parse("O=MGM-${UUID.randomUUID()}, L=London, C=GB").toString()

        private lateinit var holdingIdentity: String
    }

    @BeforeEach
    fun beforeEach() {
        outputStub = OutputStub()
    }

    @Test
    fun `onboarding MGM with default options succeeds`() {
        val mgm = mgmName()
        CommandLine(OnboardMgm()).execute(
            mgm,
            targetUrl,
            user,
            password,
            INSECURE,
        )

        outputStub.lookup(mgm)
        assertEquals(mgm, outputStub.getFirstPartyName())
    }

    @Test
    fun `onboarding MGM with provided CPI hash succeeds`() {
        val command = OnboardMgm()

        CommandLine(command).execute(
            mgmName(),
            targetUrl,
            user,
            password,
            INSECURE,
        )
        val cpiHash = command.getExistingCpiHash()

        val mgm = mgmName()
        CommandLine(OnboardMgm()).execute(
            mgm,
            "--cpi-hash=$cpiHash",
            targetUrl,
            user,
            password,
            INSECURE,
        )

        outputStub.lookup(mgm)
        assertEquals(mgm, outputStub.getFirstPartyName())
    }

    @Test
    fun `onboarding MGM saves group policy to file`() {
        val groupPolicyLocation = "${System.getProperty("user.home")}/.corda/gp/test.json"
        CommandLine(OnboardMgm()).execute(
            mgmName(),
            "-s=$groupPolicyLocation",
            targetUrl,
            user,
            password,
            INSECURE,
        )

        val groupPolicyFile = File(
            File(File(File(System.getProperty("user.home")), ".corda"), "gp"),
            "test.json",
        )
        assertThat(groupPolicyFile.exists()).isTrue
        assertThat(ObjectMapper().readTree(groupPolicyFile.inputStream()).get("groupId")).isNotNull
    }

    @Test
    fun `onboarding MGM saves group ID to file`() {
        CommandLine(OnboardMgm()).execute(
            mgmName(),
            targetUrl,
            user,
            password,
            INSECURE,
        )

        assertThat(
            File(
                File(File(File(System.getProperty("user.home")), ".corda"), "groupId"),
                "groupId.txt",
            ).exists(),
        ).isTrue
    }

    @Test
    fun `onboarding MGM with mutual TLS sets correct TLS type in group policy`() {
        CommandLine(OnboardMgm()).execute(
            mgmName(),
            "--mutual-tls",
            targetUrl,
            user,
            password,
            INSECURE,
        )

        assertThat(
            File(
                File(File(File(System.getProperty("user.home")), ".corda"), "gp"),
                "groupPolicy.json",
            ).readText().contains("\"corda.group.tls.type\": \"Mutual\""),
        )
    }

    @Test
    fun `onboarding MGM with provided gateway URLs sets correct registration context`() {
        val mgm = mgmName()
        val gatewayUrl0 = "https://localhost:8080"
        val gatewayUrl1 = "https://localhost:8081"
        CommandLine(OnboardMgm()).execute(
            mgm,
            "--p2p-gateway-url=$gatewayUrl0",
            "--p2p-gateway-url=$gatewayUrl1",
            targetUrl,
            user,
            password,
            INSECURE,
        )

        outputStub.lookup(mgm)
        val output = outputStub.printedOutput?.get(0)?.get("memberContext")
        assertThat(output).isNotNull
        assertThat(output!!.get("corda.endpoints.0.connectionURL")?.asText()).isEqualTo(gatewayUrl0)
        assertThat(output.get("corda.endpoints.1.connectionURL")?.asText()).isEqualTo(gatewayUrl1)
    }

    private fun OnboardMgm.getExistingCpiHash(): String {
        return createRestClient(CpiUploadRestResource::class).use { client ->
            val response = client.start().proxy.getAllCpis()
            response.cpis
                .first { it.groupPolicy?.contains("CREATE_ID") == true }
                .cpiFileChecksum
        }
    }

    private fun OutputStub.lookup(mgmName: String) {
        holdingIdentity = HoldingIdentityUtils.getHoldingIdentity(
            null,
            mgmName,
            null,
        )
        CommandLine(MemberLookup(this)).execute(
            "-h=$holdingIdentity",
            targetUrl,
            user,
            password,
            INSECURE,
        )
    }
}
