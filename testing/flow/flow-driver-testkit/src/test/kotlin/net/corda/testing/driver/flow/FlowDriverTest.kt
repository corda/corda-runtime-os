package net.corda.testing.driver.flow

import com.fasterxml.jackson.databind.ObjectMapper
import com.r3.corda.demo.consensual.ConsensualDemoFlow
import com.r3.corda.demo.mandelbrot.CalculateBlockFlow
import com.r3.corda.demo.mandelbrot.RequestMessage
import com.r3.corda.demo.utxo.UtxoDemoFlow
import java.util.concurrent.TimeUnit.MINUTES
import java.util.stream.Stream
import net.corda.testing.driver.AllTestsDriver
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.VirtualNodeInfo
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
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
        driver.run { dsl ->
            virtualNodes += dsl.startNode(setOf(alice, bob)).onEach { vnode ->
                logger.info("VirtualNode({}): {}", vnode.holdingIdentity.x500Name, vnode)
            }
        }
        logger.info("{} and {} started successfully", alice.commonName, bob.commonName)
    }

    @ParameterizedTest
    @ArgumentsSource(RequestProvider::class)
    fun testRun(request: RequestMessage) {
        val mandelbrot = virtualNodes.filter { vnode ->
            vnode.cpiIdentifier.name == "mandelbrot"
        }

        val aliceResult = driver.let { dsl ->
            dsl.runFlow(mandelbrot.single { it.holdingIdentity.x500Name == alice }, CalculateBlockFlow::class.java) {
                jsonMapper.writeValueAsString(request)
            }
        }
        logger.info("Alice Mandelbrot Block={}", aliceResult)

        val bobResult = driver.let { dsl ->
            dsl.runFlow(mandelbrot.single { it.holdingIdentity.x500Name == bob }, CalculateBlockFlow::class.java) {
                jsonMapper.writeValueAsString(request)
            }
        }
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
    fun testConsensualLedger() {
        val consensual = virtualNodes.single { vnode ->
            vnode.cpiIdentifier.name == "ledger-consensual-demo-app" && vnode.holdingIdentity.x500Name == bob
        }

        val result = driver.let { dsl ->
            dsl.runFlow(consensual, ConsensualDemoFlow::class.java) {
                val request = ConsensualDemoFlow.InputMessage("foo", listOf(alice.toString(), bob.toString()))
                jsonMapper.writeValueAsString(request)
            }
        }
        logger.info("Consensual Demo={}", result)
    }

    @Disabled("Requires notary support")
    @Test
    fun testUtxoLedger() {
        val utxo = virtualNodes.single { vnode ->
            vnode.cpiIdentifier.name == "ledger-utxo-demo-app" && vnode.holdingIdentity.x500Name == alice
        }

        val result = driver.let { dsl ->
            dsl.runFlow(utxo, UtxoDemoFlow::class.java) {
                val request = UtxoDemoFlow.InputMessage("foo", listOf(alice.toString(), bob.toString()), "notary")
                jsonMapper.writeValueAsString(request)
            }
        }
        logger.info("UTXO Demo={}", result)
    }
}
