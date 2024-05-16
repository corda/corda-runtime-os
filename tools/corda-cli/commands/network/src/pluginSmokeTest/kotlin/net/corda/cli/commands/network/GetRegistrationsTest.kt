package net.corda.cli.commands.network

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.cli.plugins.network.utils.HoldingIdentityUtils
import net.corda.e2etest.utilities.DEFAULT_CLUSTER
import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import picocli.CommandLine
import java.io.File
import java.util.UUID

class GetRegistrationsTest {
    private companion object {
        private lateinit var outputStub: OutputStub

        private val targetUrl = "--target=${DEFAULT_CLUSTER.rest.uri}"
        private val user = "--user=${DEFAULT_CLUSTER.rest.user}"
        private val password = "--password=${DEFAULT_CLUSTER.rest.password}"
        private const val INSECURE = "--insecure=true"

        private val mgmName = MemberX500Name.parse("O=MGM-${UUID.randomUUID()}, L=London, C=GB").toString()
        private fun memberName() = MemberX500Name.parse("O=Member-${UUID.randomUUID()}, L=London, C=GB")
        private val groupPolicyFile = File(
            File(File(File(System.getProperty("user.home")), ".corda"), "gp"),
            "groupPolicy.json",
        )
        private val cpbLocation = this::class.java.classLoader.getResource("test-cordapp.cpb")!!.path

        @BeforeAll
        @JvmStatic
        fun setup() {
            CommandLine(OnboardMgm()).execute(
                mgmName,
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
    fun `get registrations with holding identity returns correct result`() {
        val member = memberName()
        CommandLine(OnboardMember()).execute(
            member.toString(),
            "--cpb-file=$cpbLocation",
            targetUrl,
            user,
            password,
            INSECURE,
        )
        val holdingIdentity = HoldingIdentityUtils.getHoldingIdentity(
            null,
            member,
            null,
        )

        CommandLine(GetRegistrations(outputStub)).execute(
            "-h=$holdingIdentity",
            targetUrl,
            user,
            password,
            INSECURE,
        )

        assertThat(outputStub.getRegistrationIds().size).isEqualTo(1)
    }

    @Test
    fun `get registrations with name and group ID returns correct result`() {
        val groupId = ObjectMapper().readTree(groupPolicyFile.inputStream()).get("groupId").asText()
        val member = memberName().toString()
        CommandLine(OnboardMember()).execute(
            member,
            "--cpb-file=$cpbLocation",
            targetUrl,
            user,
            password,
            INSECURE,
        )

        CommandLine(GetRegistrations(outputStub)).execute(
            "-n=$member",
            "-g=$groupId",
            targetUrl,
            user,
            password,
            INSECURE,
        )

        assertThat(outputStub.getRegistrationIds().size).isEqualTo(1)
    }

    @Test
    fun `get registrations with only name provided uses group ID of last created group`() {
        val member = memberName().toString()
        CommandLine(OnboardMember()).execute(
            member,
            "--cpb-file=$cpbLocation",
            targetUrl,
            user,
            password,
            INSECURE,
        )

        CommandLine(GetRegistrations(outputStub)).execute(
            "-n=$member",
            targetUrl,
            user,
            password,
            INSECURE,
        )

        assertThat(outputStub.getRegistrationIds().size).isEqualTo(1)
    }

    @Test
    fun `get registrations with request ID provided returns correct registration request`() {
        val member = memberName().toString()
        CommandLine(OnboardMember()).execute(
            member,
            "--cpb-file=$cpbLocation",
            targetUrl,
            user,
            password,
            INSECURE,
        )

        CommandLine(GetRegistrations(outputStub)).execute(
            "-n=$member",
            targetUrl,
            user,
            password,
            INSECURE,
        )

        val requests = outputStub.getRegistrationIds()
        assertThat(requests.size).isEqualTo(1)
        CommandLine(GetRegistrations(outputStub)).execute(
            "-n=$member",
            "--request-id=${requests.first()}",
            targetUrl,
            user,
            password,
            INSECURE,
        )
        assertThat(outputStub.getRegistrationIds()).containsOnly(requests.first())
    }

    private fun OutputStub.getRegistrationIds(): Collection<String> {
        val ids = mutableSetOf<String>()
        return printedOutput?.mapNotNullTo(ids) { it.get("registrationId")?.asText() }
            ?: emptySet()
    }
}
