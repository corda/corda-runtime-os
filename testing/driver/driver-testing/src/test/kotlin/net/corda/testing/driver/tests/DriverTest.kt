package net.corda.testing.driver.tests

import java.util.concurrent.TimeUnit.MINUTES
import net.corda.testing.driver.DriverDSL
import net.corda.testing.driver.DriverNodes
import net.corda.v5.base.types.MemberX500Name
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import org.slf4j.LoggerFactory

@Suppress("JUnitMalformedDeclaration")
@Timeout(5, unit = MINUTES)
@TestInstance(PER_CLASS)
class DriverTest {
    private val alice = MemberX500Name.parse("CN=Alice, OU=Testing, O=R3, L=London, C=GB")
    private val bob = MemberX500Name.parse("CN=Bob, OU=Testing, O=R3, L=San Francisco, C=US")
    private val lucy = MemberX500Name.parse("CN=Lucy, OU=Testing, O=R3, L=Rome, C=IT")
    private val logger = LoggerFactory.getLogger(DriverTest::class.java)

    @RegisterExtension
    private val driver = DriverNodes(alice, bob).withNotary(lucy, 1).forEachTest()

    @BeforeAll
    fun sanityCheck() {
        // Ensure that we use the corda-driver bundle rather than a directory of its classes.
        assertThat(DriverNodes::class.java.protectionDomain.codeSource.location.path).endsWith(".jar")
    }

    private fun DriverDSL.testNodesFor(member: MemberX500Name) {
        val nodes = startNodes(setOf(member)).onEach { vNode ->
            logger.info("VirtualNode({}): {}", vNode.holdingIdentity.x500Name, vNode)
        }
        assertThat(nodes).hasSize(2)
        logger.info("{} started successfully", member.commonName)
    }

    @Test
    fun testStartNodes() {
        driver.run { dsl ->
            dsl.testNodesFor(alice)
            dsl.testNodesFor(bob)
        }
    }
}
