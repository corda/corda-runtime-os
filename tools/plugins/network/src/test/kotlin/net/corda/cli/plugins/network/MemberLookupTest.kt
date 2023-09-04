package net.corda.cli.plugins.network

import com.fasterxml.jackson.databind.JsonNode
import net.corda.cli.plugins.network.output.Output
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import net.corda.e2etest.utilities.DEFAULT_CLUSTER
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import net.corda.cli.plugins.network.utils.HoldingIdentityUtils
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED
import org.junit.jupiter.api.BeforeAll
import picocli.CommandLine
import net.corda.v5.base.types.MemberX500Name
import org.junit.jupiter.api.BeforeEach
import java.io.File

class MemberLookupTest {
    companion object {
        private lateinit var memberLookup: MemberLookup
        private lateinit var outputStub: OutputStub
        private lateinit var onboardMgm: OnboardMgm

        private val CLI_PARAMS = arrayOf(
            "--target=${DEFAULT_CLUSTER.rest.uri}",
            "--user=${DEFAULT_CLUSTER.rest.user}",
            "--password=${DEFAULT_CLUSTER.rest.password}",
            "--insecure=true"
        )

        private val mgm = MemberX500Name.parse(
            "CN=Alice, OU=R3 Test, O=Mgm, L=London, ST=Tottenham, C=GB"
        )

        private lateinit var holdingIdentity: String

        @BeforeAll
        @JvmStatic
        fun setup() {
            onboardMgm = OnboardMgm()
            CommandLine(onboardMgm).execute(
                mgm.toString(),
                CLI_PARAMS[0],
                CLI_PARAMS[1],
                CLI_PARAMS[2],
                CLI_PARAMS[3]
            )
            holdingIdentity = HoldingIdentityUtils.getHoldingIdentity(
                null,
                onboardMgm.name,
                null
            )
        }
    }

    @BeforeEach
    fun beforeEach() {
        outputStub = OutputStub()
        memberLookup = MemberLookup(outputStub)
    }

    @Test
    fun `test member lookup command with status filter with ACTIVE`() {
        CommandLine(memberLookup).execute(
            "--status=${MEMBER_STATUS_ACTIVE}",
            "-h=${holdingIdentity}",
            CLI_PARAMS[0],
            CLI_PARAMS[1],
            CLI_PARAMS[2],
            CLI_PARAMS[3]
        )

        val mgmContext = outputStub.printedOutput?.get(0)?.get("mgmContext")
        assertEquals(MEMBER_STATUS_ACTIVE, mgmContext?.get(MemberInfoExtension.STATUS)?.asText())
    }

    @Test
    fun `test member lookup command with status filter with SUSPENDED`() {
        CommandLine(memberLookup).execute(
            "--status=${MEMBER_STATUS_SUSPENDED}",
            "-h=${holdingIdentity}",
            CLI_PARAMS[0],
            CLI_PARAMS[1],
            CLI_PARAMS[2],
            CLI_PARAMS[3]
        )

        assertEquals(JsonNodeFactory.instance.arrayNode(), outputStub.printedOutput)
    }

    @Test
    fun `test member lookup command with Common Name (CN)`() {
        CommandLine(memberLookup).execute(
            "-cn=${mgm.commonName}",
            "-h=${holdingIdentity}",
            CLI_PARAMS[0],
            CLI_PARAMS[1],
            CLI_PARAMS[2],
            CLI_PARAMS[3]
        )

        assertEquals(onboardMgm.name, outputStub.getFirstPartyName())
    }

    @Test
    fun `test member lookup command with Organisation Unit (OU)`() {
        CommandLine(memberLookup).execute(
            "-ou=${mgm.organizationUnit}",
            "-h=${holdingIdentity}",
            CLI_PARAMS[0],
            CLI_PARAMS[1],
            CLI_PARAMS[2],
            CLI_PARAMS[3]
        )

        assertEquals(onboardMgm.name, outputStub.getFirstPartyName())
    }

    @Test
    fun `test member lookup command with Locality (L)`() {
        CommandLine(memberLookup).execute(
            "-l=${mgm.locality}",
            "-h=${holdingIdentity}",
            CLI_PARAMS[0],
            CLI_PARAMS[1],
            CLI_PARAMS[2],
            CLI_PARAMS[3]
        )

        assertEquals(onboardMgm.name, outputStub.getFirstPartyName())
    }

    @Test
    fun `test member lookup command with State (ST)`() {
        CommandLine(memberLookup).execute(
            "-st=${mgm.state}",
            "-h=${holdingIdentity}",
            CLI_PARAMS[0],
            CLI_PARAMS[1],
            CLI_PARAMS[2],
            CLI_PARAMS[3]
        )

        assertEquals(onboardMgm.name, outputStub.getFirstPartyName())
    }

    @Test
    fun `test member lookup command with Country (C)`() {
        CommandLine(memberLookup).execute(
            "-c=${mgm.country}",
            "-h=${holdingIdentity}",
            CLI_PARAMS[0],
            CLI_PARAMS[1],
            CLI_PARAMS[2],
            CLI_PARAMS[3]
        )

        assertEquals(onboardMgm.name, outputStub.getFirstPartyName())
    }

    @Test
    fun `test member lookup command with Organisation (O)`() {
        CommandLine(memberLookup).execute(
            "-o=${mgm.organization}",
            "-h=${holdingIdentity}",
            CLI_PARAMS[0],
            CLI_PARAMS[1],
            CLI_PARAMS[2],
            CLI_PARAMS[3]
        )

        assertEquals(onboardMgm.name, outputStub.getFirstPartyName())
    }

    @Test
    fun `test member lookup command with X500 name with default groupId from file`() {
        CommandLine(memberLookup).execute(
            "--name=${onboardMgm.name}",
            CLI_PARAMS[0],
            CLI_PARAMS[1],
            CLI_PARAMS[2],
            CLI_PARAMS[3]
        )

        assertEquals(onboardMgm.name, outputStub.getFirstPartyName())
    }

    @Test
    fun `test member lookup command with X500 name with a custom groupId`() {
        val group = File(
            File(File(File(System.getProperty("user.home")), ".corda"), "groupId"),
            "groupId.txt"
        ).readText().trim()
        CommandLine(memberLookup).execute(
            "--name=${onboardMgm.name}",
            "--group=${group}",
            CLI_PARAMS[0],
            CLI_PARAMS[1],
            CLI_PARAMS[2],
            CLI_PARAMS[3]
        )

        assertEquals(onboardMgm.name, outputStub.getFirstPartyName())
    }

    @Test
    fun `test member lookup command with holding identity short hash`() {
        CommandLine(memberLookup).execute(
            "-h=${holdingIdentity}",
            CLI_PARAMS[0],
            CLI_PARAMS[1],
            CLI_PARAMS[2],
            CLI_PARAMS[3]
        )

        assertEquals(onboardMgm.name, outputStub.getFirstPartyName())
    }

    private fun OutputStub.getFirstPartyName(): String? {
        return printedOutput?.get(0)?.get("memberContext")?.get(MemberInfoExtension.PARTY_NAME)?.asText()
    }

    private class OutputStub : Output {
        private val objectMapper = ObjectMapper()
        var printedOutput: JsonNode? = null

        override fun generateOutput(content: String) {
            printedOutput = objectMapper.readTree(content)
        }
    }
}