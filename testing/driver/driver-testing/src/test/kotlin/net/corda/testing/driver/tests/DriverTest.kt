package net.corda.testing.driver.tests

import java.util.concurrent.TimeUnit.MINUTES
import net.corda.testing.driver.EachTestDriver
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
    private val logger = LoggerFactory.getLogger(DriverTest::class.java)

    @RegisterExtension
    private val driver = EachTestDriver(alice)

    @BeforeAll
    fun sanityCheck() {
        // Ensure that we use the corda-driver bundle rather than a directory of its classes.
        assertThat(EachTestDriver::class.java.protectionDomain.codeSource.location.path).endsWith(".jar")
    }

    @Test
    fun testStartNode() {
        driver.run { dsl ->
            val aliceNodes = dsl.startNode(setOf(alice)).onEach { vNode ->
                logger.info("VirtualNode({}): {}", vNode.holdingIdentity.x500Name, vNode)
            }
            assertThat(aliceNodes).hasSize(2)
        }

        logger.info("{} started successfully", alice.commonName)
    }
}
