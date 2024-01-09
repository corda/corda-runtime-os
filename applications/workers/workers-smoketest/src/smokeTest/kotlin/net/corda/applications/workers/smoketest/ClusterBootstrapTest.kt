package net.corda.applications.workers.smoketest

import net.corda.e2etest.utilities.ClusterReadiness
import net.corda.e2etest.utilities.ClusterReadinessChecker
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.time.Duration

@Order(2)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ClusterBootstrapTest : ClusterReadiness by ClusterReadinessChecker() {
    @Test
    fun checkCluster() {
        assertIsReady(Duration.ofMinutes(5), Duration.ofSeconds(1))
    }
}