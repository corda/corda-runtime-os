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

class MemberLookupTest {

    companion object {
        private lateinit var memberLookup: MemberLookup
        private lateinit var onboardMgm: OnboardMgm
        private lateinit var outputStub: OutputStub

        private val CLI_PARAMS = arrayOf(
            "--target=${DEFAULT_CLUSTER.rest.uri}",
            "--user=${DEFAULT_CLUSTER.rest.user}",
            "--password=${DEFAULT_CLUSTER.rest.password}",
            "--insecure=${true}"
        )

        private val X500 = arrayOf(
            "CN=Alice",
            "OU=R3 Test",
            "O=Mgm",
            "L=London",
            "ST=Tottenham",
            "C=GB"
            )

        @BeforeAll
        @JvmStatic
        fun setup() {
            outputStub = OutputStub()
            onboardMgm = OnboardMgm()
            memberLookup = MemberLookup(outputStub)

            CommandLine(onboardMgm).execute(
                X500.joinToString(separator = ", "),
                *CLI_PARAMS
            )
        }
    }

    @Test
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
        assertEquals(ACTIVE.value, mgmContext?.get(MemberInfoExtension.STATUS)?.asText() ?: "")
    }

    @Test
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
    fun `test member lookup command with Common Name (CN)`() {
        CommandLine(memberLookup).execute(
            "-cn=${X500[0].split("=")[1]}",
            "-h=${HoldingIdentityUtils.getHoldingIdentity(
                null,
                onboardMgm.name,
                null
            )}",
            *CLI_PARAMS
        )

        val memberContext = outputStub.printedOutput?.get(0)?.get("memberContext")
        assertEquals(onboardMgm.name, memberContext?.get(MemberInfoExtension.PARTY_NAME)?.asText() ?: "")
    }

    @Test
    fun `test member lookup command with Organisation Unit (OU)`() {
        CommandLine(memberLookup).execute(
            "-ou=${X500[1].split("=")[1]}",
            "-h=${HoldingIdentityUtils.getHoldingIdentity(
                null,
                onboardMgm.name,
                null
            )}",
            *CLI_PARAMS
        )

        val memberContext = outputStub.printedOutput?.get(0)?.get("memberContext")
        assertEquals(onboardMgm.name, memberContext?.get(MemberInfoExtension.PARTY_NAME)?.asText() ?: "")
    }

    @Test
    fun `test member lookup command with Locality (L)`() {
        CommandLine(memberLookup).execute(
            "-l=${X500[3].split("=")[1]}",
            "-h=${HoldingIdentityUtils.getHoldingIdentity(
                null,
                onboardMgm.name,
                null
            )}",
            *CLI_PARAMS
        )

        val memberContext = outputStub.printedOutput?.get(0)?.get("memberContext")
        assertEquals(onboardMgm.name, memberContext?.get(MemberInfoExtension.PARTY_NAME)?.asText() ?: "")
    }

    @Test
    fun `test member lookup command with State (ST)`() {
        CommandLine(memberLookup).execute(
            "-st=${X500[4].split("=")[1]}",
            "-h=${HoldingIdentityUtils.getHoldingIdentity(
                null,
                onboardMgm.name,
                null
            )}",
            *CLI_PARAMS
        )

        val memberContext = outputStub.printedOutput?.get(0)?.get("memberContext")
        assertEquals(onboardMgm.name, memberContext?.get(MemberInfoExtension.PARTY_NAME)?.asText() ?: "")
    }

    @Test
    fun `test member lookup command with Country (C)`() {
        CommandLine(memberLookup).execute(
            "-c=${X500[5].split("=")[1]}",
            "-h=${HoldingIdentityUtils.getHoldingIdentity(
                null,
                onboardMgm.name,
                null
            )}",
            *CLI_PARAMS
        )

        val memberContext = outputStub.printedOutput?.get(0)?.get("memberContext")
        assertEquals(onboardMgm.name, memberContext?.get(MemberInfoExtension.PARTY_NAME)?.asText() ?: "")
    }

    @Test
    fun `test member lookup command with Organisation (O)`() {
        CommandLine(memberLookup).execute(
            "-o=${X500[2].split("=")[1]}",
            "-h=${HoldingIdentityUtils.getHoldingIdentity(
                null,
                onboardMgm.name,
                null
            )}",
            *CLI_PARAMS
        )

        val memberContext = outputStub.printedOutput?.get(0)?.get("memberContext")
        assertEquals(onboardMgm.name, memberContext?.get(MemberInfoExtension.PARTY_NAME)?.asText() ?: "")
    }

    @Test
    fun `test member lookup command with X500 name with default groupId from file`() {
        CommandLine(memberLookup).execute(
            "--name=${onboardMgm.name}",
            *CLI_PARAMS
        )

        val memberContext = outputStub.printedOutput?.get(0)?.get("memberContext")
        assertEquals(onboardMgm.name, memberContext?.get(MemberInfoExtension.PARTY_NAME)?.asText() ?: "")
    }

    @Test
    fun `test member lookup command with holding identity short hash`() {
        CommandLine(memberLookup).execute(
            "-h=${HoldingIdentityUtils.getHoldingIdentity(
                null,
                onboardMgm.name,
                null
            )}",
            *CLI_PARAMS
        )

        val memberContext = outputStub.printedOutput?.get(0)?.get("memberContext")
        assertEquals(onboardMgm.name, memberContext?.get(MemberInfoExtension.PARTY_NAME)?.asText() ?: "")
    }

    private class OutputStub : Output {
        private val objectMapper = ObjectMapper()
        var printedOutput: JsonNode? = null

        override fun generateOutput(content: String) {
            printedOutput = objectMapper.readTree(content)
        }
    }
}