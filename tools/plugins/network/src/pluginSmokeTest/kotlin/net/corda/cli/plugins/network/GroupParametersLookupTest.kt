package net.corda.cli.plugins.network

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.cli.plugins.network.utils.HoldingIdentityUtils
import net.corda.crypto.core.ShortHash
import net.corda.e2etest.utilities.DEFAULT_CLUSTER
import net.corda.membership.lib.GroupParametersNotaryUpdater.Companion.EPOCH_KEY
import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import picocli.CommandLine
import java.io.File
import java.util.UUID

class GroupParametersLookupTest {
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
    fun `group parameters lookup with holding identity returns correct result`() {
        CommandLine(GroupParametersLookup(outputStub)).execute(
            "-h=$holdingIdentity",
            targetUrl,
            user,
            password,
            INSECURE,
        )

        val output = outputStub.printedOutput?.get("parameters")
        assertThat(output).isNotNull
        assertThat(output!!.get(EPOCH_KEY).asText()).isEqualTo("1")
    }

    @Test
    fun `group parameters lookup with name and group ID returns correct result`() {
        val groupId = ObjectMapper().readTree(groupPolicyFile.inputStream()).get("groupId").asText()

        CommandLine(GroupParametersLookup(outputStub)).execute(
            "-n=$mgmName",
            "-g=$groupId",
            targetUrl,
            user,
            password,
            INSECURE,
        )

        val output = outputStub.printedOutput?.get("parameters")
        assertThat(output).isNotNull
        assertThat(output!!.get(EPOCH_KEY).asText()).isEqualTo("1")
    }

    @Test
    fun `group parameters lookup with only name provided uses group ID of last created group`() {
        CommandLine(GroupParametersLookup(outputStub)).execute(
            "-n=$mgmName",
            targetUrl,
            user,
            password,
            INSECURE,
        )

        val output = outputStub.printedOutput?.get("parameters")
        assertThat(output).isNotNull
        assertThat(output!!.get(EPOCH_KEY).asText()).isEqualTo("1")
    }
}
