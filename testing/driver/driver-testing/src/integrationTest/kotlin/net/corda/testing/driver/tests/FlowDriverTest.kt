package net.corda.testing.driver.tests

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.r3.corda.demo.mandelbrot.CalculateBlockFlow
import com.r3.corda.demo.mandelbrot.RequestMessage
import com.r3.corda.testing.smoketests.flow.RpcSmokeTestFlow
import com.r3.corda.testing.smoketests.flow.messages.RpcSmokeTestInput
import java.util.concurrent.TimeUnit.MINUTES
import java.util.stream.Stream
import net.corda.testing.driver.AllTestsDriver
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.VirtualNodeInfo
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import org.slf4j.LoggerFactory

@Timeout(5, unit = MINUTES)
@TestInstance(PER_CLASS)
class FlowDriverTest {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private val alice = MemberX500Name.parse("CN=Alice, OU=Testing, O=R3, L=London, C=GB")
    private val bob = MemberX500Name.parse("CN=Bob, OU=Testing, O=R3, L=San Francisco, C=US")
    private val virtualNodes = mutableSetOf<VirtualNodeInfo>()
    private val jsonMapper = ObjectMapper()

    @RegisterExtension
    private val driver = AllTestsDriver(alice, bob)

    @BeforeAll
    fun start() {
        // Ensure that we use the corda-driver bundle rather than a directory of its classes.
        assertThat(AllTestsDriver::class.java.protectionDomain.codeSource.location.path).endsWith(".jar")

        driver.run { dsl ->
            virtualNodes += dsl.startNode(setOf(alice, bob)).onEach { vNode ->
                logger.info("VirtualNode({}): {}", vNode.holdingIdentity.x500Name, vNode)
            }
        }
        logger.info("{} and {} started successfully", alice.commonName, bob.commonName)
    }

    @ParameterizedTest
    @ArgumentsSource(RequestProvider::class)
    fun testMandelbrotFlow(request: RequestMessage) {
        val mandelbrot = virtualNodes.filter { vNode ->
            vNode.cpiIdentifier.name == "mandelbrot"
        }

        val aliceResult = driver.let { dsl ->
            dsl.runFlow(mandelbrot.single { it.holdingIdentity.x500Name == alice }, CalculateBlockFlow::class.java) {
                jsonMapper.writeValueAsString(request)
            }
        } ?: fail("aliceResult must not be null")
        logger.info("Alice Mandelbrot Block={}", aliceResult)

        val bobResult = driver.let { dsl ->
            dsl.runFlow(mandelbrot.single { it.holdingIdentity.x500Name == bob }, CalculateBlockFlow::class.java) {
                jsonMapper.writeValueAsString(request)
            }
        } ?: fail("bobResult must not be null")
        logger.info("Bob Mandelbrot Block={}", bobResult)
    }

    class RequestProvider : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext): Stream<out Arguments> {
            return Stream.of(
                Arguments.of(createRequestMessage(100.0, 20.2)),
                Arguments.of(createRequestMessage(253.7, -10.1)),
                Arguments.of(createRequestMessage(854.9, 120.6)),
                Arguments.of(createRequestMessage(-577.2, 88.8)),
                Arguments.of(createRequestMessage(14.5, 37.3))
            )
        }

        private fun createRequestMessage(startX: Double, startY: Double): RequestMessage {
            return RequestMessage().apply {
                this.startX = startX
                this.startY = startY
                this.width = 50.0
                this.height = 50.0
            }
        }
    }

    @Test
    fun testSmokeTestFlow() {
        val testCorDapp = virtualNodes.single { vNode ->
            vNode.cpiIdentifier.name == "test-cordapp" && vNode.holdingIdentity.x500Name == alice
        }

        val flowResult = driver.let { dsl ->
            dsl.runFlow(testCorDapp, RpcSmokeTestFlow::class.java) {
                val request = RpcSmokeTestInput().apply {
                    command = "flow_messaging_apis"
                    data = mapOf("sessions" to bob.toString())
                }
                jsonMapper.writeValueAsString(request)
            }
        } ?: fail("flowResult must not be null")
        logger.info("SmokeTest result={}", flowResult)

        assertThat(jsonMapper.readValue(flowResult, object : TypeReference<Map<String, Any>>() {}))
            .hasEntrySatisfying("command") { command -> assertEquals("flow_messaging_apis", command) }
            .hasEntrySatisfying("result") { result -> assertEquals("$bob=Completed. Sum:18", result) }
    }
}
