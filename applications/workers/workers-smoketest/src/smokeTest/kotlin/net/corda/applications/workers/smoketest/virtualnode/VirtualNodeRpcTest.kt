package net.corda.applications.workers.smoketest.virtualnode

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.applications.workers.smoketest.CLUSTER_URI
import net.corda.applications.workers.smoketest.ERROR_HOLDING_ID
import net.corda.applications.workers.smoketest.FLOW_WORKER_DEV_CPI_NAME
import net.corda.applications.workers.smoketest.GROUP_ID
import net.corda.applications.workers.smoketest.PASSWORD
import net.corda.applications.workers.smoketest.USERNAME
import net.corda.applications.workers.smoketest.X500_ALICE
import net.corda.applications.workers.smoketest.toJson
import net.corda.applications.workers.smoketest.truncateLongHash
import net.corda.applications.workers.smoketest.virtualnode.helpers.assertWithRetry
import net.corda.applications.workers.smoketest.virtualnode.helpers.cluster
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder

// The CPB we're using in this test
const val TEST_CPB = "/META-INF/flow-worker-dev.cpb"

/**
 * Any 'unordered' tests are run *last*
 */
@Order(10)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class VirtualNodeRpcTest {
    companion object {
        // Some simple test failure messages
        private const val ERROR_CPI_NOT_UPLOADED =
            "CPI has not been uploaded during this run - this test needs to be run on a clean cluster."
        private const val ERROR_IS_CLUSTER_RUNNING = "Initial upload failed - is the cluster running?"

        // Server side messages
        // BUG: These server-side messages can change arbitrarily - not ideal at the moment, but we return all errors with code '500' and no other info.
        private const val EXPECTED_ERROR_NO_GROUP_POLICY = "CPI is missing a group policy file"
        private const val EXPECTED_ERROR_ALREADY_UPLOADED = "CPI already uploaded with groupId"

    }

    /**
     * As long as no-one assigns an order lower than this, this test runs first, and all others, after, which is fine.
     *
     * This *first* test, uploads a CPI into the system.  It must pass.
     */
    @Test
    @Order(10)
    fun `can upload CPI`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)

            val requestId = cpiUpload(TEST_CPB, GROUP_ID).let { it.toJson()["id"].textValue() }
            assertThat(requestId).withFailMessage(ERROR_IS_CLUSTER_RUNNING).isNotEmpty

            // BUG:  returning "OK" feels 'weakly' typed
            val json = assertWithRetry {
                command { cpiStatus(requestId) }
                condition { it.code == 200 && it.toJson()["status"].textValue() == "OK" }
            }.toJson()

            val cpiHash = json["checksum"].textValue()
            assertThat(cpiHash).isNotNull.isNotEmpty

            // Capture the cpiHash from the cpi status upload
            // We probably want more tests like this that enforce "expectations" on the API.
            assertThat(cpiHash!!.length)
                .withFailMessage("Short code length of wrong size - likely this test needs fixing")
                .isEqualTo(12)

            // Compare it to the hash in the cpi list - should be identical.
            // CORE-4475: tests fixed behaviour previously reported as a bug
            val cpis = cpiList().toJson()
            val cpiJson = cpis["cpis"].first()
            val actualChecksum = truncateLongHash(cpiJson["fileChecksum"].textValue())

            assertThat(actualChecksum).isNotNull.isNotEmpty

            assertThat(cpiHash).withFailMessage(ERROR_CPI_NOT_UPLOADED).isNotNull

            assertThat(actualChecksum).isEqualTo(cpiHash)
        }
    }

    /**
     * Runs second to ensure that we reject this with a correct message
     */
    @Test
    @Order(20)
    fun `cannot upload CPI without group policy file aka CPB`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)

            val requestId = cpbUpload(TEST_CPB).let { it.toJson()["id"].textValue() }
            assertThat(requestId).withFailMessage(ERROR_IS_CLUSTER_RUNNING).isNotEmpty

            val json = assertWithRetry {
                command { cpiStatus(requestId) }
                condition { it.code == 500 }
            }.toJson()

            val titleJson = ObjectMapper().readTree(json["title"].textValue())
            assertThat(titleJson["errorMessage"].textValue()).isEqualTo(EXPECTED_ERROR_NO_GROUP_POLICY)
        }
    }

    @Test
    @Order(30)
    fun `cannot upload same CPI`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)
            val requestId = cpiUpload(TEST_CPB, GROUP_ID).let { it.toJson()["id"].textValue() }
            assertThat(requestId).withFailMessage(ERROR_IS_CLUSTER_RUNNING).isNotEmpty

            val json = assertWithRetry {
                command { cpiStatus(requestId) }
                condition { it.code == 500 }
            }.toJson()

            val titleJson = ObjectMapper().readTree(json["title"].textValue())
            assertThat(titleJson["errorMessage"].textValue().startsWith(EXPECTED_ERROR_ALREADY_UPLOADED)).isTrue()
        }
    }

    @Test
    @Order(33)
    fun `list cpis`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)

            val json = assertWithRetry {
                command { cpiList() }
                condition { it.code == 200 }
            }.toJson()

            assertThat(json["cpis"].size()).isGreaterThan(0)
        }
    }

    @Test
    @Order(37)
    fun `list cpis and check group id matches value in group policy file`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)

            val json = cpiList().toJson()
            val cpiJson = json["cpis"].first()

            val groupPolicyJson = cpiJson["groupPolicy"].textValue().toJson()
            assertThat(groupPolicyJson["groupId"].textValue()).isEqualTo(GROUP_ID)
        }
    }

    @Test
    @Order(40)
    fun `can create virtual node with holding id and CPI`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)
            val cpis = cpiList().toJson()["cpis"]
            val json = cpis.toList().first { it["id"]["cpiName"].textValue() == FLOW_WORKER_DEV_CPI_NAME }
            val hash = truncateLongHash(json["fileChecksum"].textValue())

            val vNodeJson = assertWithRetry {
                command { vNodeCreate(hash, X500_ALICE) }
                condition { it.code == 200 }
                failMessage(ERROR_HOLDING_ID)
            }.toJson()

            assertThat(vNodeJson["holdingIdHash"].textValue()).isNotNull.isNotEmpty
        }
    }

    @Test
    @Order(50)
    fun `cannot create duplicate virtual node`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)
            val cpis = cpiList().toJson()["cpis"]
            val json = cpis.toList().first { it["id"]["cpiName"].textValue() == FLOW_WORKER_DEV_CPI_NAME }
            val hash = truncateLongHash(json["fileChecksum"].textValue())

            assertWithRetry {
                command { vNodeCreate(hash, X500_ALICE) }
                condition { it.code == 500 }
            }
        }
    }

    @Test
    @Order(60)
    fun `list virtual nodes`() {
        cluster {
            endpoint(CLUSTER_URI, USERNAME, PASSWORD)
            val json = vNodeList().toJson()["virtualNodes"].first()
            val actualX500Name = json["holdingIdentity"]["x500Name"].textValue()

            assertThat(actualX500Name).isEqualTo(X500_ALICE)
        }
    }
}
