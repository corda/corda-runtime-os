package net.corda.cli.commands.network

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import net.corda.cli.commands.network.utils.HoldingIdentityUtils
import net.corda.crypto.core.ShortHash
import net.corda.e2etest.utilities.DEFAULT_CLUSTER
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED
import net.corda.v5.base.types.MemberX500Name
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import picocli.CommandLine
import java.io.File

class MemberLookupTest {
    companion object {
        private lateinit var memberLookup: MemberLookup
        private lateinit var outputStub: OutputStub
        private lateinit var onboardMgm: OnboardMgm

        private val targetUrl = "--target=${DEFAULT_CLUSTER.rest.uri}"
        private val user = "--user=${DEFAULT_CLUSTER.rest.user}"
        private val password = "--password=${DEFAULT_CLUSTER.rest.password}"
        private const val INSECURE = "--insecure=true"

        private val mgm = MemberX500Name.parse(
            "CN=Alice, OU=R3 Test, O=Mgm, L=London, ST=Tottenham, C=GB",
        )

        private lateinit var holdingIdentity: ShortHash

        @BeforeAll
        @JvmStatic
        fun setup() {
            onboardMgm = OnboardMgm()
            CommandLine(onboardMgm).execute(
                mgm.toString(),
                targetUrl,
                user,
                password,
                INSECURE,
            )
            holdingIdentity = HoldingIdentityUtils.getHoldingIdentity(
                null,
                onboardMgm.name,
                null,
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
            "--status=$MEMBER_STATUS_ACTIVE",
            "-h=$holdingIdentity",
            targetUrl,
            user,
            password,
            INSECURE,
        )

        val mgmContext = outputStub.printedOutput?.get(0)?.get("mgmContext")
        assertEquals(MEMBER_STATUS_ACTIVE, mgmContext?.get(MemberInfoExtension.STATUS)?.asText())
    }

    @Test
    fun `test member lookup command with status filter with SUSPENDED`() {
        CommandLine(memberLookup).execute(
            "--status=$MEMBER_STATUS_SUSPENDED",
            "-h=$holdingIdentity",
            targetUrl,
            user,
            password,
            INSECURE,
        )

        assertEquals(JsonNodeFactory.instance.arrayNode(), outputStub.printedOutput)
    }

    @Test
    fun `test member lookup command with Common Name (CN)`() {
        CommandLine(memberLookup).execute(
            "-cn=${mgm.commonName}",
            "-h=$holdingIdentity",
            targetUrl,
            user,
            password,
            INSECURE,
        )

        assertEquals(onboardMgm.name, outputStub.getFirstPartyName())
    }

    @Test
    fun `test member lookup command with Organisation Unit (OU)`() {
        CommandLine(memberLookup).execute(
            "-ou=${mgm.organizationUnit}",
            "-h=$holdingIdentity",
            targetUrl,
            user,
            password,
            INSECURE,
        )

        assertEquals(onboardMgm.name, outputStub.getFirstPartyName())
    }

    @Test
    fun `test member lookup command with Locality (L)`() {
        CommandLine(memberLookup).execute(
            "-l=${mgm.locality}",
            "-h=$holdingIdentity",
            targetUrl,
            user,
            password,
            INSECURE,
        )

        assertEquals(onboardMgm.name, outputStub.getFirstPartyName())
    }

    @Test
    fun `test member lookup command with State (ST)`() {
        CommandLine(memberLookup).execute(
            "-st=${mgm.state}",
            "-h=$holdingIdentity",
            targetUrl,
            user,
            password,
            INSECURE,
        )

        assertEquals(onboardMgm.name, outputStub.getFirstPartyName())
    }

    @Test
    fun `test member lookup command with Country (C)`() {
        CommandLine(memberLookup).execute(
            "-c=${mgm.country}",
            "-h=$holdingIdentity",
            targetUrl,
            user,
            password,
            INSECURE,
        )

        assertEquals(onboardMgm.name, outputStub.getFirstPartyName())
    }

    @Test
    fun `test member lookup command with Organisation (O)`() {
        CommandLine(memberLookup).execute(
            "-o=${mgm.organization}",
            "-h=$holdingIdentity",
            targetUrl,
            user,
            password,
            INSECURE,
        )

        assertEquals(onboardMgm.name, outputStub.getFirstPartyName())
    }

    @Test
    fun `test member lookup command with X500 name with default groupId from file`() {
        CommandLine(memberLookup).execute(
            "--name=${onboardMgm.name}",
            targetUrl,
            user,
            password,
            INSECURE,
        )

        assertEquals(onboardMgm.name, outputStub.getFirstPartyName())
    }

    @Test
    fun `test member lookup command with X500 name with a custom groupId`() {
        val group = File(
            File(File(File(System.getProperty("user.home")), ".corda"), "groupId"),
            "groupId.txt",
        ).readText().trim()
        CommandLine(memberLookup).execute(
            "--name=${onboardMgm.name}",
            "--group=$group",
            targetUrl,
            user,
            password,
            INSECURE,
        )

        assertEquals(onboardMgm.name, outputStub.getFirstPartyName())
    }

    @Test
    fun `test member lookup command with holding identity short hash`() {
        CommandLine(memberLookup).execute(
            "-h=$holdingIdentity",
            targetUrl,
            user,
            password,
            INSECURE,
        )

        assertEquals(onboardMgm.name, outputStub.getFirstPartyName())
    }
}
