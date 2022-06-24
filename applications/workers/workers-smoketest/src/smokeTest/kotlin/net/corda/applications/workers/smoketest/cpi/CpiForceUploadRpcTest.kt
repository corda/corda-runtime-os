package net.corda.applications.workers.smoketest.cpi

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.applications.workers.smoketest.CLUSTER_URI
import net.corda.applications.workers.smoketest.PASSWORD
import net.corda.applications.workers.smoketest.USERNAME
import net.corda.applications.workers.smoketest.truncateLongHash
import net.corda.applications.workers.smoketest.virtualnode.helpers.assertWithRetry
import net.corda.applications.workers.smoketest.virtualnode.helpers.cluster
import net.corda.test.util.eventually
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.time.Duration
import java.time.Instant
import net.corda.applications.workers.smoketest.CALCULATOR_CPI_NAME
import net.corda.applications.workers.smoketest.ERROR_HOLDING_ID
import net.corda.applications.workers.smoketest.GROUP_ID
import net.corda.applications.workers.smoketest.X500_CAROL
import net.corda.applications.workers.smoketest.awaitRpcFlowFinished
import net.corda.applications.workers.smoketest.getCpiChecksum
import net.corda.applications.workers.smoketest.getHoldingIdShortHash
import net.corda.applications.workers.smoketest.startRpcFlow
import net.corda.applications.workers.smoketest.toJson
import net.corda.applications.workers.smoketest.virtualnode.helpers.ClusterBuilder

const val ADDITION_CALCULATOR_CPB = "/META-INF/calculator.cpb"
const val MULTIPLICATION_CALCULATOR_CPB = "/META-INF/cache-invalidation-testing/calculator.cpb"

/**
 * Any 'unordered' tests are run *last*
 */
@Order(30)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class CpiForceUploadRpcTest {
    companion object {
        private const val ERROR_CPI_NOT_UPLOADED =
            "CPI has not been uploaded during this run - this test needs to be run on a clean cluster."
        private const val ERROR_IS_CLUSTER_RUNNING =
            "Initial upload failed - is the cluster running?"

        private var carolHoldingId: String = getHoldingIdShortHash(X500_CAROL, GROUP_ID)
    }

    @Test
    @Order(30)
    fun `can upload the addition calculator`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)

            val requestId = cpiUpload(ADDITION_CALCULATOR_CPB, GROUP_ID).let { it.toJson()["id"].textValue() }
            assertThat(requestId).withFailMessage(ERROR_IS_CLUSTER_RUNNING).isNotEmpty

            val json = assertWithRetry {
                command { cpiStatus(requestId) }
                condition { it.code == 200 && it.toJson()["status"].textValue() == "OK" }
            }.toJson()

            val cpiHash = json["checksum"].textValue()
            assertThat(cpiHash).isNotNull.isNotEmpty

            assertThat(cpiHash!!.length)
                .withFailMessage("Short code length of wrong size - likely this test needs fixing")
                .isEqualTo(12)

            val cpiChecksum = getCpiChecksum(CALCULATOR_CPI_NAME)

            assertThat(cpiChecksum).isNotNull.isNotEmpty
            assertThat(cpiHash).withFailMessage(ERROR_CPI_NOT_UPLOADED).isNotNull
            assertThat(cpiChecksum).isEqualTo(cpiHash)
        }
    }

    @Test
    @Order(31)
    fun `create a clean virtual node`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)
            val hash = getCpiChecksum(CALCULATOR_CPI_NAME)

            val vNodeJson = assertWithRetry {
                command { vNodeCreate(hash, X500_CAROL) }
                condition { it.code == 200 }
                failMessage(ERROR_HOLDING_ID)
            }.toJson()

            assertThat(vNodeJson["holdingIdHash"].textValue()).isNotNull.isNotEmpty
        }
    }
    @Test
    @Order(32)
    fun `can run the uploaded CPI - addition calculator`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)

            runCalculatorFlow(carolHoldingId, 10, 20, 10 + 20)
        }
    }

    @Test
    @Order(33)
    fun `can force upload the exact same CPI`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)

            val initialCpkTimeStamp = getCpkTimestamp(CALCULATOR_CPI_NAME)

            val requestId = forceCpiUpload(ADDITION_CALCULATOR_CPB, GROUP_ID).let { it.toJson()["id"].textValue() }
            assertThat(requestId).withFailMessage(ERROR_IS_CLUSTER_RUNNING).isNotEmpty

            assertWithRetry {
                command { cpiStatus(requestId) }
                condition { it.code == 200 && it.toJson()["status"].textValue() == "OK" }
            }

            eventually(Duration.ofSeconds(20)) {
                assertThat(getCpkTimestamp(CALCULATOR_CPI_NAME)).isAfter(initialCpkTimeStamp)
            }
        }
    }

    @Test
    @Order(34)
    fun `can force upload multiplication calculator with the same CpkId but performs multiplication and has different checksum`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)

            val initialCpkTimeStamp = getCpkTimestamp(CALCULATOR_CPI_NAME)

            val requestId = forceCpiUpload(MULTIPLICATION_CALCULATOR_CPB, GROUP_ID).let { it.toJson()["id"].textValue() }
            assertThat(requestId).withFailMessage(ERROR_IS_CLUSTER_RUNNING).isNotEmpty

            assertWithRetry {
                command { cpiStatus(requestId) }
                condition { it.code == 200 && it.toJson()["status"].textValue() == "OK" }
            }

            eventually(Duration.ofSeconds(20)) {
                assertThat(getCpkTimestamp(CALCULATOR_CPI_NAME)).isAfter(initialCpkTimeStamp)
            }
        }
    }

    @Test
    @Order(35)
    fun `can run the newly uploaded CPI - multiplication calculator`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)

            runCalculatorFlow(carolHoldingId, 10, 20, 10 * 20)
        }
    }

    @Test
    @Order(36)
    fun `can force upload the original CPI - addition calculator and check that it is usable`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)

            val initialCpkTimeStamp = getCpkTimestamp(CALCULATOR_CPI_NAME)

            val requestId = forceCpiUpload(ADDITION_CALCULATOR_CPB, GROUP_ID).let { it.toJson()["id"].textValue() }
            assertThat(requestId).withFailMessage(ERROR_IS_CLUSTER_RUNNING).isNotEmpty

            assertWithRetry {
                command { cpiStatus(requestId) }
                condition { it.code == 200 && it.toJson()["status"].textValue() == "OK" }
            }

            eventually(Duration.ofSeconds(20)) {
                assertThat(getCpkTimestamp(CALCULATOR_CPI_NAME)).isAfter(initialCpkTimeStamp)
            }

            runCalculatorFlow(carolHoldingId, 10, 20, 10 + 20)
        }
    }

    private fun runCalculatorFlow(holdingIdentity: String, a: Int, b: Int, expectedResult: Int) {
        val className = "net.cordapp.testing.calculator.CalculatorFlow"

        val requestId = startRpcFlow(holdingIdentity, mapOf("a" to a, "b" to b), className)

        val flowStatus = awaitRpcFlowFinished(holdingIdentity, requestId)

        val resultJson = ObjectMapper().readTree(flowStatus.flowResult)
        assertThat(resultJson["result"].intValue()).isEqualTo(expectedResult)
    }

    private fun ClusterBuilder.getCpkTimestamp(cpiName: String): Instant {
        val cpis = cpiList().toJson()["cpis"]
        val cpiJson = cpis.toList().first { it["id"]["cpiName"].textValue() == cpiName }
        val cpksJson = cpiJson["cpks"].toList()
        return cpksJson.first()["timestamp"].toInstant()
    }

    private fun JsonNode.toInstant(): Instant {
        return Instant.parse(this.asText())
    }
}
