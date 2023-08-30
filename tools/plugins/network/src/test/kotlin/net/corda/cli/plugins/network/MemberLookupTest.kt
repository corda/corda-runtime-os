package net.corda.cli.plugins.network

import com.fasterxml.jackson.databind.JsonNode
import net.corda.cli.plugins.network.output.Output
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import net.corda.e2etest.utilities.DEFAULT_CLUSTER
import net.corda.e2etest.utilities.conditionallyUploadCpiSigningCertificate
import com.fasterxml.jackson.databind.ObjectMapper

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class SkipInitialization

class MemberLookupTest {

    private lateinit var outputStub: OutputStub
    private lateinit var memberLookup: MemberLookup
    private lateinit var onboardMgm: OnboardMgm

    @BeforeEach
    internal fun beforeEach(testInfo: TestInfo) {
        val hasSkipAnnotation = testInfo.testMethod
            .filter { it.isAnnotationPresent(SkipInitialization::class.java) }
            .isPresent

        if (!hasSkipAnnotation) {
            DEFAULT_CLUSTER.conditionallyUploadCpiSigningCertificate()
        }

        outputStub = OutputStub()
        onboardMgm = OnboardMgm()
        memberLookup = MemberLookup(outputStub)

        onboardMgm.targetUrl = DEFAULT_CLUSTER.rest.uri.toString()
        onboardMgm.username = DEFAULT_CLUSTER.rest.user
        onboardMgm.password = DEFAULT_CLUSTER.rest.password
        onboardMgm.insecure = true

        memberLookup.targetUrl = DEFAULT_CLUSTER.rest.uri.toString()
        memberLookup.username = DEFAULT_CLUSTER.rest.user
        memberLookup.password = DEFAULT_CLUSTER.rest.password
        memberLookup.insecure = true

        onboardMgm.name = "O=Mgm, L=London, C=GB"
        onboardMgm.run()
    }

    @Test
    @SkipInitialization
    fun `test member lookup command with status filter`() {
        memberLookup.holdingIdentityShortHash = onboardMgm.holdingId
        memberLookup.status = listOf("ACTIVE", "SUSPENDED")
        memberLookup.run()

        val mgmContext = outputStub.printedOutput?.get(0)?.get("mgmContext")
        assertEquals("ACTIVE", mgmContext?.get("corda.status")?.asText() ?: "")
    }

    @Test
    @SkipInitialization
    fun `test member lookup command with X500 name`() {
        memberLookup.name = onboardMgm.name
        memberLookup.run()

        val memberContext = outputStub.printedOutput?.get(0)?.get("memberContext")
        assertEquals(onboardMgm.name, memberContext?.get("corda.name")?.asText() ?: "")
    }

    @Test
    fun `test member lookup command with holding identity short hash`() {
        memberLookup.holdingIdentityShortHash = onboardMgm.holdingId
        memberLookup.run()

        val memberContext = outputStub.printedOutput?.get(0)?.get("memberContext")
        assertEquals(onboardMgm.name, memberContext?.get("corda.name")?.asText() ?: "")
    }

    private class OutputStub : Output {
        private val objectMapper = ObjectMapper()
        var printedOutput: JsonNode? = null

        override fun generateOutput(content: String) {
            printedOutput = objectMapper.readTree(content)
        }
    }
}