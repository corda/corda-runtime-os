package net.corda.testing.driver.test

import java.util.concurrent.TimeUnit.MINUTES
import net.corda.testing.driver.EachTestDriver
import net.corda.v5.base.types.MemberX500Name
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import org.slf4j.LoggerFactory

@Timeout(5, unit = MINUTES)
class DriverTest {
    private companion object {
        private val logger = LoggerFactory.getLogger(DriverTest::class.java)
    }

    @RegisterExtension
    private val driver = EachTestDriver()

    @Test
    fun testRun() {
        val alice = MemberX500Name.parse("CN=Alice, OU=Testing, O=R3, L=London, C=GB")
        val bob = MemberX500Name.parse("CN=Bob, OU=Testing, O=R3, L=San Francisco, C=US")

        driver.run { dsl ->
            dsl.startNode(setOf(alice)).forEach { vnode ->
                logger.info("VirtualNode({}): {}", vnode.holdingIdentity.x500Name, vnode)
            }

            dsl.startNode(setOf(bob)).forEach { vnode ->
                logger.info("VirtualNode({}): {}", vnode.holdingIdentity.x500Name, vnode)
            }
        }

        logger.info("{} and {} ran successfully", alice.commonName, bob.commonName)
    }
}
