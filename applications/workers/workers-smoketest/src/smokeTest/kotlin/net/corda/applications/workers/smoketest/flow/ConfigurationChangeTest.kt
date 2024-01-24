package net.corda.applications.workers.smoketest.flow

import net.corda.applications.workers.smoketest.utils.TEST_CPB_LOCATION
import net.corda.applications.workers.smoketest.utils.TEST_CPI_NAME
import net.corda.e2etest.utilities.ClusterReadiness
import net.corda.e2etest.utilities.ClusterReadinessChecker
import net.corda.e2etest.utilities.DEFAULT_CLUSTER
import net.corda.e2etest.utilities.REST_FLOW_STATUS_SUCCESS
import net.corda.e2etest.utilities.RestSmokeTestInput
import net.corda.e2etest.utilities.awaitRestFlowResult
import net.corda.e2etest.utilities.conditionallyUploadCordaPackage
import net.corda.e2etest.utilities.conditionallyUploadCpiSigningCertificate
import net.corda.e2etest.utilities.config.configWithDefaultsNode
import net.corda.e2etest.utilities.config.getConfig
import net.corda.e2etest.utilities.config.managedConfig
import net.corda.e2etest.utilities.config.waitForConfigurationChange
import net.corda.e2etest.utilities.getHoldingIdShortHash
import net.corda.e2etest.utilities.getOrCreateVirtualNodeFor
import net.corda.e2etest.utilities.registerStaticMember
import net.corda.e2etest.utilities.startRestFlow
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import net.corda.schema.configuration.MessagingConfig.MAX_ALLOWED_MSG_SIZE
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.parallel.Isolated
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.UUID

@Suppress("Unused", "FunctionName")
@Order(Int.MAX_VALUE)
@TestInstance(Lifecycle.PER_CLASS)
@Isolated("As this test updates the config which affects running cluster")
class ConfigurationChangeTest : ClusterReadiness by ClusterReadinessChecker() {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private val testRunUniqueId = UUID.randomUUID()
        private val groupId = UUID.randomUUID().toString()
        private val applicationCpiName = "${TEST_CPI_NAME}_$testRunUniqueId"
        private val bobX500 = "CN=Bob-$testRunUniqueId, OU=Application, O=R3, L=London, C=GB"
        private var bobHoldingId: String = getHoldingIdShortHash(bobX500, groupId)
        private val charlyX500 = "CN=Charley-$testRunUniqueId, OU=Application, O=R3, L=London, C=GB"
        private var charlieHoldingId: String = getHoldingIdShortHash(charlyX500, groupId)
        private val staticMemberList = listOf(
            bobX500,
            charlyX500,
        )
    }

    @BeforeAll
    internal fun beforeAll() {
        // check cluster is ready
        assertIsReady(Duration.ofMinutes(1), Duration.ofMillis(100))

        DEFAULT_CLUSTER.conditionallyUploadCpiSigningCertificate()

        // Upload test flows if not already uploaded
        conditionallyUploadCordaPackage(
            applicationCpiName, TEST_CPB_LOCATION, groupId, staticMemberList
        )

        // Make sure Virtual Nodes are created
        val bobActualHoldingId = getOrCreateVirtualNodeFor(bobX500, applicationCpiName)
        val charlieActualHoldingId = getOrCreateVirtualNodeFor(charlyX500, applicationCpiName)

        // Just validate the function and actual vnode holding ID hash are in sync
        // if this fails the X500_BOB formatting could have changed or the hash implementation might have changed
        assertThat(bobActualHoldingId).isEqualTo(bobHoldingId)
        assertThat(charlieActualHoldingId).isEqualTo(charlieHoldingId)

        registerStaticMember(bobHoldingId)
        registerStaticMember(charlieHoldingId)
    }
    
    @AfterAll
    internal fun afterAll() {
        // check cluster is ready when done with this test
        assertIsReady(Duration.ofMinutes(1), Duration.ofMillis(100))
    }
    
    @Test
    fun `cluster configuration changes are picked up and workers continue to operate normally`() {
        val currentConfigValue = getConfig(MESSAGING_CONFIG).configWithDefaultsNode()[MAX_ALLOWED_MSG_SIZE].asInt()
        val newConfigurationValue = (currentConfigValue * 1.5).toInt()

        logger.info("Set new config")
        managedConfig()
            .load(MESSAGING_CONFIG, MAX_ALLOWED_MSG_SIZE, newConfigurationValue).apply {
            // Wait for the rest-worker to reload the configuration and come back up
            logger.info("Wait for the rest-worker to reload the configuration and come back up")
            waitForConfigurationChange(MESSAGING_CONFIG, MAX_ALLOWED_MSG_SIZE, newConfigurationValue.toString(), false)

            //Ensure cluster is ready after Config Update.
            assertIsReady(Duration.ofMinutes(1), Duration.ofMillis(100))

            // Execute some flows which require functionality from different workers and make sure they succeed
            logger.info("Execute some flows which require functionality from different workers and make sure they succeed")
            val flowIds = mutableListOf(
                startRestFlow(
                    bobHoldingId,
                    RestSmokeTestInput().apply {
                        command = "persistence_persist"
                        data = mapOf("id" to UUID.randomUUID().toString())
                    }
                ),

                startRestFlow(
                    bobHoldingId,
                    RestSmokeTestInput().apply {
                        command = "crypto_sign_and_verify"
                        data = mapOf("memberX500" to bobX500)
                    }
                ),

                startRestFlow(
                    bobHoldingId,
                    RestSmokeTestInput().apply {
                        command = "lookup_member_by_x500_name"
                        data = mapOf("id" to charlyX500)
                    }
                )
            )

            logger.info("Check status of flows")
            flowIds.forEach {
                val flowResult = awaitRestFlowResult(bobHoldingId, it)
                assertThat(flowResult.flowError).isNull()
                assertThat(flowResult.flowStatus).isEqualTo(REST_FLOW_STATUS_SUCCESS)
            }
        }
    }
}
