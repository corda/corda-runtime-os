package net.corda.testing.driver.tests

import com.r3.corda.testing.smoketests.flow.AmqpSerializationTestFlow
import java.util.concurrent.TimeUnit.MINUTES
import net.corda.testing.driver.DriverNodes
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.VirtualNodeInfo
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import org.slf4j.LoggerFactory

@Suppress("FunctionName", "JUnitMalformedDeclaration")
@Timeout(5, unit = MINUTES)
@TestInstance(PER_CLASS)
class AMQPSerializationTests {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private val alice = MemberX500Name.parse("CN=Alice, OU=Testing, O=R3, L=London, C=GB")
    private val virtualNodes = mutableSetOf<VirtualNodeInfo>()

    @RegisterExtension
    private val driver = DriverNodes(alice).forAllTests()

    @BeforeAll
    fun start() {
        driver.run { dsl ->
            virtualNodes += dsl.startNode(setOf(alice)).onEach { vNode ->
                logger.info("VirtualNode({}): {}", vNode.holdingIdentity.x500Name, vNode)
            }
        }
        logger.info("{} started successfully", alice.commonName)
    }

    @Test
    fun `serialize and deserialize a Pair`() {
        val testCorDapp = virtualNodes.single { vNode ->
            vNode.cpiIdentifier.name == "test-cordapp" && vNode.holdingIdentity.x500Name == alice
        }

        val flowResult = driver.let { dsl ->
            dsl.runFlow(testCorDapp, AmqpSerializationTestFlow::class.java) { "" }
        } ?: fail("flowResult must not be null")
        logger.info("AMQPSerializationTest result={}", flowResult)

        assertThat(flowResult).isEqualTo("SerializableClass(pair=(A, B))")
    }
}
