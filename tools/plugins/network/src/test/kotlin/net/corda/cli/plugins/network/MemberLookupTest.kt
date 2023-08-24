package net.corda.cli.plugins.network

import net.corda.cli.plugins.network.output.Output
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import net.corda.e2etest.utilities.DEFAULT_CLUSTER
import net.corda.e2etest.utilities.conditionallyUploadCpiSigningCertificate


class MemberLookupTest {

    private lateinit var outputStub: OutputStub
    private lateinit var memberLookup: MemberLookup
    private lateinit var onboardMgm: OnboardMgm

    @BeforeEach
    fun setup() {
        DEFAULT_CLUSTER.conditionallyUploadCpiSigningCertificate()
        outputStub = OutputStub()
        onboardMgm = OnboardMgm()
        memberLookup = MemberLookup(outputStub)
    }

    @Test
    fun `test member lookup command`() {
        // Set the required options or arguments
        onboardMgm.name = "O=Mgm, L=London, C=GB"
        onboardMgm.targetUrl = "https://localhost:8888"
        onboardMgm.username = "admin"
        onboardMgm.password = "admin"
        onboardMgm.run()
        memberLookup.holdingIdentityShortHash = "abc123"
        memberLookup.name = "O=Mgm, L=London, C=GB"

        // Invoke the command
        memberLookup.run()

        // Assert the expected output or behavior
        assertEquals("Member lookup: holdingIdentityShortHash=abc123, name=O=Mgm, L=London, C=GB", outputStub.printedOutput)
    }

    // Stub implementation of the Output interface
    private class OutputStub : Output {
        var printedOutput: String = ""

        override fun generateOutput(content: String) {
            // Do nothing for this test
        }
    }
}