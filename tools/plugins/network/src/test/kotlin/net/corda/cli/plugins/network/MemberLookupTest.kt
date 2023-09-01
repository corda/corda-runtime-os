package net.corda.cli.plugins.network

import com.fasterxml.jackson.databind.JsonNode
import net.corda.cli.plugins.network.output.Output
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import net.corda.e2etest.utilities.DEFAULT_CLUSTER
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import net.corda.cli.plugins.network.enums.MemberStatus.ACTIVE
import net.corda.cli.plugins.network.enums.MemberStatus.SUSPENDED
import net.corda.cli.plugins.network.utils.HoldingIdentityUtils
import net.corda.membership.lib.MemberInfoExtension
import org.junit.jupiter.api.BeforeAll
import picocli.CommandLine
import net.corda.v5.base.types.MemberX500Name
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Order

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

        private val x500 = MemberX500Name.parse(
            "CN=Alice, OU=R3 Test, O=Mgm, L=London, ST=Tottenham, C=GB"
        )

        @BeforeAll
        @JvmStatic
        fun setup() {
            onboardMgm = OnboardMgm()
            CommandLine(onboardMgm).execute(
                x500.toString(),
                *CLI_PARAMS
            )
        }
    }

    @BeforeEach
    fun beforeEach() {
        outputStub = OutputStub()
        memberLookup = MemberLookup(outputStub)
    }

    @Test
    @Order(1)
    fun `test member lookup command with status filter with ACTIVE`() {
        CommandLine(memberLookup).execute(
            "--status=${ACTIVE}",
            "-h=${HoldingIdentityUtils.getHoldingIdentity(
                null,
                onboardMgm.name,
                null
            )}",
            *CLI_PARAMS
        )

        val mgmContext = outputStub.printedOutput?.get(0)?.get("mgmContext")
        assertEquals(ACTIVE.value, mgmContext?.get(MemberInfoExtension.STATUS)?.asText())
    }

    @Test
    @Order(2)
    fun `test member lookup command with status filter with SUSPENDED`() {
        CommandLine(memberLookup).execute(
            "--status=${SUSPENDED}",
            "-h=${HoldingIdentityUtils.getHoldingIdentity(
                null,
                onboardMgm.name,
                null
            )}",
            *CLI_PARAMS
        )

        assertEquals(JsonNodeFactory.instance.arrayNode(), outputStub.printedOutput)
    }

    @Test
    @Order(3)
    fun `test member lookup command with Common Name (CN)`() {
        CommandLine(memberLookup).execute(
            "-cn=${x500.commonName}",
            "-h=${HoldingIdentityUtils.getHoldingIdentity(
                null,
                onboardMgm.name,
                null
            )}",
            *CLI_PARAMS
        )

        assertEquals(onboardMgm.name, outputStub.getFirstPartyName())
    }

    @Test
    @Order(4)
    fun `test member lookup command with Organisation Unit (OU)`() {
        CommandLine(memberLookup).execute(
            "-ou=${x500.organizationUnit}",
            "-h=${HoldingIdentityUtils.getHoldingIdentity(
                null,
                onboardMgm.name,
                null
            )}",
            *CLI_PARAMS
        )

        assertEquals(onboardMgm.name, outputStub.getFirstPartyName())
    }

    @Test
    @Order(5)
    fun `test member lookup command with Locality (L)`() {
        CommandLine(memberLookup).execute(
            "-l=${x500.locality}",
            "-h=${HoldingIdentityUtils.getHoldingIdentity(
                null,
                onboardMgm.name,
                null
            )}",
            *CLI_PARAMS
        )

        assertEquals(onboardMgm.name, outputStub.getFirstPartyName())
    }

    @Test
    @Order(6)
    fun `test member lookup command with State (ST)`() {
        CommandLine(memberLookup).execute(
            "-st=${x500.state}",
            "-h=${HoldingIdentityUtils.getHoldingIdentity(
                null,
                onboardMgm.name,
                null
            )}",
            *CLI_PARAMS
        )

        assertEquals(onboardMgm.name, outputStub.getFirstPartyName())
    }

    @Test
    @Order(7)
    fun `test member lookup command with Country (C)`() {
        CommandLine(memberLookup).execute(
            "-c=${x500.country}",
            "-h=${HoldingIdentityUtils.getHoldingIdentity(
                null,
                onboardMgm.name,
                null
            )}",
            *CLI_PARAMS
        )

        assertEquals(onboardMgm.name, outputStub.getFirstPartyName())
    }

    @Test
    @Order(8)
    fun `test member lookup command with Organisation (O)`() {
        CommandLine(memberLookup).execute(
            "-o=${x500.organization}",
            "-h=${HoldingIdentityUtils.getHoldingIdentity(
                null,
                onboardMgm.name,
                null
            )}",
            *CLI_PARAMS
        )

        assertEquals(onboardMgm.name, outputStub.getFirstPartyName())
    }

    @Test
    @Order(9)
    fun `test member lookup command with X500 name with default groupId from file`() {
        CommandLine(memberLookup).execute(
            "--name=${onboardMgm.name}",
            *CLI_PARAMS
        )

        assertEquals(onboardMgm.name, outputStub.getFirstPartyName())
    }

    @Test
    @Order(10)
    fun `test member lookup command with holding identity short hash`() {
        CommandLine(memberLookup).execute(
            "-h=${HoldingIdentityUtils.getHoldingIdentity(
                null,
                onboardMgm.name,
                null
            )}",
            *CLI_PARAMS
        )

        assertEquals(onboardMgm.name, outputStub.getFirstPartyName())
    }

    private fun OutputStub.getFirstPartyName(): String? {
        return printedOutput?.get(0)?.get("memberContext")?.get(MemberInfoExtension.PARTY_NAME)?.asText()
    }

    class OutputStub : Output {
        private val objectMapper = ObjectMapper()
        var printedOutput: JsonNode? = null

        override fun generateOutput(content: String) {
            printedOutput = objectMapper.readTree(content)
        }
    }
}