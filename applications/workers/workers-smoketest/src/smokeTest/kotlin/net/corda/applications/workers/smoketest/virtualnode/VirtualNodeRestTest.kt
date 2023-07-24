package net.corda.applications.workers.smoketest.virtualnode

import java.util.UUID
import net.corda.applications.workers.smoketest.utils.ERROR_CPI_NOT_UPLOADED
import net.corda.applications.workers.smoketest.utils.TEST_CPB_LOCATION
import net.corda.applications.workers.smoketest.utils.TEST_CPI_NAME
import net.corda.applications.workers.smoketest.utils.VNODE_UPGRADE_TEST_CPI_NAME
import net.corda.applications.workers.smoketest.utils.VNODE_UPGRADE_TEST_CPI_V1
import net.corda.applications.workers.smoketest.utils.VNODE_UPGRADE_TEST_CPI_V2
import net.corda.applications.workers.smoketest.utils.eventuallyUploadCpi
import net.corda.e2etest.utilities.CODE_SIGNER_CERT
import net.corda.e2etest.utilities.CODE_SIGNER_CERT_ALIAS
import net.corda.e2etest.utilities.CODE_SIGNER_CERT_USAGE
import net.corda.e2etest.utilities.ClusterBuilder
import net.corda.e2etest.utilities.DEFAULT_CLUSTER
import net.corda.e2etest.utilities.assertWithRetry
import net.corda.e2etest.utilities.cluster
import net.corda.e2etest.utilities.conditionallyUploadCpiSigningCertificate
import net.corda.e2etest.utilities.getHoldingIdShortHash
import net.corda.e2etest.utilities.getOrCreateVirtualNodeFor
import net.corda.e2etest.utilities.truncateLongHash
import net.corda.test.util.eventually
import net.corda.utilities.seconds
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.TestMethodOrder

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class SkipInitialization

/**
 * Any 'unordered' tests are run *last*
 */
@Order(10)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class VirtualNodeRestTest {
    companion object {
        // Some simple test failure messages
        private val testRunUniqueId = UUID.randomUUID()
        private val groupId = UUID.randomUUID().toString()
        private val aliceX500 = "CN=Alice-$testRunUniqueId, OU=Application, O=R3, L=London, C=GB"
        private val aliceHoldingId: String = getHoldingIdShortHash(aliceX500, groupId)

        private val cpiName = "${TEST_CPI_NAME}_$testRunUniqueId"
        private val upgradeTestingCpiName = "${VNODE_UPGRADE_TEST_CPI_NAME}_$testRunUniqueId"

        private val retryTimeout = 120.seconds
        private val retryInterval = 1.seconds
    }

    @BeforeEach
    internal fun beforeEach(testInfo: TestInfo) {
        val hasSkipAnnotation = testInfo.testMethod
            .filter { it.isAnnotationPresent(SkipInitialization::class.java) }
            .isPresent

        if (!hasSkipAnnotation) {
            DEFAULT_CLUSTER.conditionallyUploadCpiSigningCertificate()
        }
    }

    @Test
    @Order(5)
    @SkipInitialization
    fun `can import codesigner certificate`() {
        cluster {
            assertWithRetry {
                // Certificate upload can be slow in the combined worker, especially after it has just started up.
                timeout(retryTimeout)
                interval(retryInterval)
                command { importCertificate(CODE_SIGNER_CERT, CODE_SIGNER_CERT_USAGE, CODE_SIGNER_CERT_ALIAS) }
                condition { it.code == 204 }
            }
        }
    }

    /**
     * This test, uploads a CPI into the system.
     */
    @Test
    @Order(10)
    fun `can upload CPI`() {
        cluster {
            val cpiHash = eventuallyUploadCpi(TEST_CPB_LOCATION, cpiName)

            val actualChecksum = getCpiChecksum(cpiName)

            assertThat(actualChecksum).isNotNull.isNotEmpty

            assertThat(cpiHash).withFailMessage(ERROR_CPI_NOT_UPLOADED).isNotNull

            assertThat(actualChecksum).isEqualTo(cpiHash)
        }
    }

    @Test
    @Order(33)
    fun `list cpis`() {
        cluster {
            val json = assertWithRetry {
                timeout(retryTimeout)
                interval(retryInterval)
                command { cpiList() }
                condition { it.code == 200 }
            }.toJson()

            assertThat(json["cpis"].size()).isGreaterThan(0)
        }
    }

    @Test
    @Order(40)
    fun `can create virtual node with holding id and CPI`() {
        cluster {
            val cpiFileChecksum = getCpiChecksum(cpiName)
            getOrCreateVirtualNodeFor(aliceX500, cpiFileChecksum)
        }
    }

    @Test
    @Order(60)
    fun `list virtual nodes`() {
        cluster {
            assertWithRetry {
                timeout(retryTimeout)
                interval(retryInterval)
                command { vNodeList() }
                condition { response ->
                    val nodes = vNodeList().toJson()["virtualNodes"].map {
                        it["holdingIdentity"]["x500Name"].textValue()
                    }
                    response.code == 200 && nodes.contains(aliceX500)
                }
            }
        }
    }

    @Test
    @Order(61)
    fun `get a virtual node`() {
        cluster {
            assertWithRetry {
                timeout(retryTimeout)
                interval(retryInterval)
                command { getVNode(aliceHoldingId) }
                condition { response ->
                    response.code == 200 &&
                            response.toJson()["holdingIdentity"]["x500Name"].textValue().contains(aliceX500)
                }
            }
        }
    }

    @Test
    @Order(110)
    fun `can upload multiple versions of a CPI with the same name`() {
        cluster {
            eventuallyUploadCpi(VNODE_UPGRADE_TEST_CPI_V1, upgradeTestingCpiName, "v1")
            eventuallyUploadCpi(VNODE_UPGRADE_TEST_CPI_V2, upgradeTestingCpiName, "v2")
        }
    }

    private fun ClusterBuilder.getCpiChecksum(cpiName: String): String {
        val cpiFileChecksum = eventually(retryTimeout, retryInterval) {
            val cpis = cpiList().toJson()["cpis"]
            val cpiJson = cpis.toList().find { it["id"]["cpiName"].textValue() == cpiName }
            assertNotNull(cpiJson, "Cpi with name $cpiName not yet found in cpi list.")
            truncateLongHash(cpiJson!!["cpiFileChecksum"].textValue())
        }
        return cpiFileChecksum
    }
}
