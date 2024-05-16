package net.corda.cli.commands.network

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.cli.plugins.network.utils.HoldingIdentityUtils
import net.corda.crypto.core.ShortHash
import net.corda.e2etest.utilities.DEFAULT_CLUSTER
import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import picocli.CommandLine
import java.io.File
import java.util.UUID

class ExportGroupPolicyTest {
    companion object {
        private lateinit var outputStub: OutputStub

        private val targetUrl = "--target=${DEFAULT_CLUSTER.rest.uri}"
        private val user = "--user=${DEFAULT_CLUSTER.rest.user}"
        private val password = "--password=${DEFAULT_CLUSTER.rest.password}"
        private const val INSECURE = "--insecure=true"

        private val mgmName = MemberX500Name.parse("O=MGM-${UUID.randomUUID()}, L=London, C=GB")
        private val groupPolicyFile = File(
            File(File(File(System.getProperty("user.home")), ".corda"), "gp"),
            "groupPolicy.json",
        )
        private lateinit var holdingIdentity: ShortHash

        @BeforeAll
        @JvmStatic
        fun setup() {
            CommandLine(OnboardMgm()).execute(
                mgmName.toString(),
                targetUrl,
                user,
                password,
                INSECURE,
            )
            holdingIdentity = HoldingIdentityUtils.getHoldingIdentity(
                null,
                mgmName,
                null,
            )
        }
    }

    @BeforeEach
    fun beforeEach() {
        outputStub = OutputStub()
    }

    @Test
    fun `exporting group policy correctly saves file to default location`() {
        groupPolicyFile.delete()
        CommandLine(ExportGroupPolicy()).execute(
            "-h=$holdingIdentity",
            targetUrl,
            user,
            password,
            INSECURE,
        )

        assertThat(groupPolicyFile.exists()).isTrue
        assertThat(ObjectMapper().readTree(groupPolicyFile.inputStream()).get("groupId")).isNotNull
    }

    @Test
    fun `exporting group policy correctly saves file to provided location`() {
        val groupPolicyLocation = "${System.getProperty("user.home")}/.corda/gp/test.json"
        val groupPolicyFile = File(
                File(File(File(System.getProperty("user.home")), ".corda"), "gp"),
        "test.json",
        )
        groupPolicyFile.delete()

        CommandLine(ExportGroupPolicy()).execute(
            "-h=$holdingIdentity",
            "--save=$groupPolicyLocation",
            targetUrl,
            user,
            password,
            INSECURE,
        )
        assertThat(groupPolicyFile.exists()).isTrue
        assertThat(ObjectMapper().readTree(groupPolicyFile.inputStream()).get("groupId")).isNotNull
    }
}
