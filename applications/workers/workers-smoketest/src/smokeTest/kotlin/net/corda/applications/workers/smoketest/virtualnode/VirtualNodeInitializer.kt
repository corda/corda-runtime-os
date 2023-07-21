package net.corda.applications.workers.smoketest.virtualnode

import net.corda.applications.workers.smoketest.TEST_CPB_LOCATION
import net.corda.applications.workers.smoketest.TEST_CPI_NAME
import net.corda.e2etest.utilities.CODE_SIGNER_CERT
import net.corda.e2etest.utilities.CODE_SIGNER_CERT_ALIAS
import net.corda.e2etest.utilities.CODE_SIGNER_CERT_USAGE
import net.corda.e2etest.utilities.ClusterBuilder
import net.corda.e2etest.utilities.assertWithRetry
import net.corda.e2etest.utilities.cluster
import net.corda.rest.ResponseCode.CONFLICT
import net.corda.utilities.seconds
import org.assertj.core.api.Assertions
import java.util.UUID

/**
 * Many of the smoke tests rely on both the codesigner certificate and the TEST_CPB to exist in the cluster.
 * The purpose of this singleton object is to persist these objects; it simply needs to be referenced in a
 * test class and the init function will handle the rest.
 */
object VirtualNodeInitializer {
    private val retryTimeout = 120.seconds
    private val retryInterval = 1.seconds

    private val testRunUniqueId = UUID.randomUUID()
    private val cpiName = "${TEST_CPI_NAME}_$testRunUniqueId"

    private val aliceX500 = "CN=Alice-$testRunUniqueId, OU=Application, O=R3, L=London, C=GB"
    private val bobX500 = "CN=Bob-$testRunUniqueId, OU=Application, O=R3, L=London, C=GB"

    private val staticMemberList = listOf(
        aliceX500,
        bobX500
    )

    private val groupId = UUID.randomUUID().toString()

    /**
     * This will run once, the first time the [VirtualNodeInitializer] is referenced in a given test run.
     * With that in mind, there's no need to do any lazy evaluation to determine if the cert and CPI exist.
     */
    init {
        uploadCertificate()
        cluster { eventuallyUploadCpi(TEST_CPB_LOCATION, cpiName) }
    }

    /**
     * Upload a codesigner certificate to the cluster in order to enable signing functionalities.
     * The certificate upload process might be slow in the combined worker, especially after
     * it has just started up. Therefore, we use a retry mechanism to handle potential delays.
     */
    private fun uploadCertificate() {
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
     * Upload a CPI to the cluster, utilizing a retry mechanism to handle potential delays or conflicts
     * that may occur during the upload.
     *
     * @return The unique hash associated with the CPI
     */
    private fun ClusterBuilder.eventuallyUploadCpi(
        cpbLocation: String,
        cpiName: String,
        cpiVersion: String = "1.0.0.0-SNAPSHOT"
    ): String {
        val requestId = cpiUpload(cpbLocation, groupId, staticMemberList, cpiName, cpiVersion)
            .let { it.toJson()["id"].textValue() }

        val json = assertWithRetry {
            timeout(retryTimeout)
            interval(retryInterval)
            command { cpiStatus(requestId) }
            condition {
                it.code == 200 && it.toJson()["status"].textValue() == "OK"
            }
            immediateFailCondition {
                it.code == CONFLICT.statusCode
                        && null != it.toJson()["details"]
                        && it.toJson()["details"]["code"].textValue().equals(CONFLICT.toString())
                        && null != it.toJson()["title"]
                        && it.toJson()["title"].textValue().contains("already uploaded")
            }
        }.toJson()

        val cpiHash = json["cpiFileChecksum"].textValue()
        Assertions.assertThat(cpiHash).isNotNull.isNotEmpty

        // Capture the cpiHash from the cpi status upload
        // We probably want more tests like this that enforce "expectations" on the API.
        Assertions.assertThat(cpiHash!!.length)
            .withFailMessage("Short code length of wrong size - likely this test needs fixing")
            .isEqualTo(12)
        return cpiHash
    }
}
