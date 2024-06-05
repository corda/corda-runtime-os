package net.corda.restclient.generated

import io.javalin.Javalin
import net.corda.restclient.CordaRestClient
import net.corda.restclient.dto.GenerateCsrWrapperRequest
import net.corda.restclient.generated.apis.CertificateApi
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.platform.commons.logging.LoggerFactory
import java.io.File
import java.net.URI

class TestKnownIssues {
    /*
    The following tests cover issues found during the implementation of the generated client.
    These issues are down to the spec giving the generator one idea, only for the actual Corda responses to be different
     */

    companion object {
        private lateinit var app: Javalin

        @BeforeAll
        @JvmStatic
        fun setup() {
            app = Javalin.create().start(0)
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            app.stop()
        }

        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val localhost = URI.create("http:localhost:${app.port()}")

    private fun assertThatCodeDoesNotThrowAnyException(code: () -> Any) {
        try {
            assertThatCode { code() }.doesNotThrowAnyException()
        } catch (e: AssertionError) {
            logger.warn { "Has the generated api been re-generated? Re-apply workaround" }
            throw e
        }
    }

    /**
     * See comment for workaround details
     * https://r3-cev.atlassian.net/browse/ES-2378?focusedCommentId=306841
     */
    @Test
    fun testGetGroupPolicyFromMgm() {
        val inputStream = File("./src/test/resources/groupPolicy.json").inputStream()
        app.get("api/v5_3/mgm/1234/info") { ctx ->
            ctx.header("Content-Type", "application/json")
            ctx.result(inputStream)
        }
        val client = CordaRestClient.createHttpClient(baseUrl = localhost)

        assertThatCodeDoesNotThrowAnyException {
            val response: String = client.mgmClient.getMgmHoldingidentityshorthashInfo("1234")
            assertThat(response).contains("\"groupId\"")
        }
    }

    /**
     * This is a known limitation with the OpenApi generator
     * https://r3-cev.atlassian.net/browse/ES-2383?focusedCommentId=307181
     */
    @Test
    fun testClusterCertRequestHandlesListCorrectly() {
        val requestConfig = CertificateApi().putCertificateClusterUsageRequestConfig(
            alias = null,
            usage = "",
            certificate = listOf(
                File("one.txt"),
                File("two.txt")
            )
        )
        assertThat(requestConfig.body?.values.toString())
            .contains("one.txt")
            .doesNotContain("two.txt")
    }

    /**
     * This is a known limitation with the OpenApi generator
     * https://r3-cev.atlassian.net/browse/ES-2383?focusedCommentId=307181
     */
    @Test
    fun testVNodeCertRequestHandlesListCorrectly() {
        val requestConfig = CertificateApi().putCertificateVnodeHoldingidentityidUsageRequestConfig(
            alias = null,
            usage = "",
            certificate = listOf(
                File("one.txt"),
                File("two.txt")
            ),
            holdingidentityid = ""
        )
        assertThat(requestConfig.body?.values.toString())
            .contains("one.txt")
            .doesNotContain("two.txt")
    }

    /**
     * See comment for workaround details
     * https://r3-cev.atlassian.net/browse/ES-2378?focusedCommentId=306841
     */
    @Test
    fun testCsrGenerationHandlesString() {
        val csrResponse = "-----BEGIN CERTIFICATE REQUEST-----\n" +
                "MIIBTjCB6gIBADBOMQswCQYDVQQGEwJHQjEPMA0GA1UEBxMGTG9uZG9uMRgwFgYD\n" +
                "VQQKEw9QMlAgQ2VydGlmaWNhdGUxFDASBgNVBAsMC1tsb2NhbGhvc3RdMFkwEwYH\n" +
                "KoZIzj0CAQYIKoZIzj0DAQcDQgAEUlCzufaLz5VxQ5Yvve8b/Q65RApxDjWCjmiQ\n" +
                "gz1dncBzaS8X9QsgJ1fL7+uV3xaPmln57n2CCLlBkrIPNGk6c6A6MDgGCSqGSIb3\n" +
                "DQEJDjErMCkwDgYDVR0PAQH/BAQDAgeAMBcGA1UdEQEB/wQNMAuCCWxvY2FsaG9z\n" +
                "dDAUBggqhkjOPQQDAgYIKoZIzj0DAQcDSQAwRgIhALCO226sNaAwxhvIT2dnnhej\n" +
                "oR+yXgQLjd7Gnt/LRo6eAiEAvGQxrZgM85IgngWBO033RYWVyxICmj/yepwDDNj+\n" +
                "SDc=\n" +
                "-----END CERTIFICATE REQUEST-----\n"
        app.post("api/v5_3/certificate/p2p/123") { ctx ->
            ctx.header("Content-Type", "application/json")
            ctx.result(csrResponse)
        }

        val client = CordaRestClient.createHttpClient(baseUrl = localhost)
        val requestBody = GenerateCsrWrapperRequest(
            x500Name = "OU=[localhost], O=P2P Certificate, L=London, C=GB",
            contextMap = null,
            subjectAlternativeNames = listOf("localhost")
        )

        assertThatCodeDoesNotThrowAnyException {
            val response: String = client.certificatesClient.postCertificateTenantidKeyid("p2p","123", requestBody)
            assertThat(response).contains("BEGIN CERTIFICATE REQUEST")
        }
    }

    /**
     * See comment for workaround details
     * https://r3-cev.atlassian.net/browse/ES-2378?focusedCommentId=306841
     */
    @Test
    fun testGetKeyInPemFormatHandlesString() {
        val keyResponse = "-----BEGIN PUBLIC KEY-----\n" +
                "MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEArARp009wi/O/lwKFPe26\n" +
                "B46cx3iILv/i6eNAzIHQK2QCelXzFyFQhJKgGe4HWtvxvwHRXv6hYtuHe4whfaue\n" +
                "T1URYKYcr5r095BCW8olEbOnfQnRq5evprQRLHFMEKQ0lWTawi9Y5jcbVL5jbvTG\n" +
                "8/FjL66AuG/hpUS9woft+e3143uLPtbM6AmV1aGEn1hdpWPH8Js7J4rhpZ56lX1h\n" +
                "KJBe2TwRsb6cp3RxcWPISCi2x39VLHOmI+9z8wBPWCqjUzX2qn4qBQwxqDPZc/eq\n" +
                "GCCzSXrSUtH1uJs3498CQKBsvRzSIicbPBvDHF6XvFk6skgR6qWVPTPjD6HmTQx7\n" +
                "bCk8s/Ai4KSAV3tPJ+x3KyDj6DnMi/aXOpwf2CIpX73I535OQk6giowdHjnsnhNM\n" +
                "mWlglNL7sMCOjhUurLdppiUafd2vpF2vd0ujFO+v377jYae/redJCRnvdRtWQ5oD\n" +
                "IDfirpEtpwLrVFejsxYh0uPrbacm30edQ2x3g5TUGozvAgMBAAE=\n" +
                "-----END PUBLIC KEY-----"
        app.get("api/v5_3/key/p2p/123") { ctx ->
            ctx.header("Content-Type", "application/json")
            ctx.result(keyResponse)
        }

        val client = CordaRestClient.createHttpClient(baseUrl = localhost)
        assertThatCodeDoesNotThrowAnyException {
            val response: String = client.keyManagementClient.getKeyTenantidKeyid("p2p", "123")
            assertThat(response).contains("-----BEGIN PUBLIC KEY-----")
        }
    }

    /**
     * See comment for workaround details
     * https://r3-cev.atlassian.net/browse/ES-2378?focusedCommentId=306841
     */
    @Test
    @Suppress("MaxLineLength")
    fun testGetSqlResponseHandlesString() {
        val sqlResponse = "CREATE TABLE IF NOT EXISTS databasechangelog (ID VARCHAR(255) NOT NULL, AUTHOR VARCHAR(255) NOT NULL, FILENAME VARCHAR(255) NOT NULL, DATEEXECUTED TIMESTAMP WITHOUT TIME ZONE NOT NULL, ORDEREXECUTED INTEGER NOT NULL, EXECTYPE VARCHAR(10) NOT NULL, MD5SUM VARCHAR(35), DESCRIPTION VARCHAR(255), COMMENTS VARCHAR(255), TAG VARCHAR(255), LIQUIBASE VARCHAR(20), CONTEXTS VARCHAR(255), LABELS VARCHAR(255), DEPLOYMENT_ID VARCHAR(10));\n" +
                "\n" +
                "-- *********************************************************************\n" +
                "-- Update Database Script\n" +
                "-- *********************************************************************\n" +
                "-- Change Log: /tmp/corda/tmp/offline-db/changelog-a0d1eff0-d055-437b-99ad-82601c4362a5.xml\n" +
                "-- Ran at: 6/3/24, 10:25 AM\n" +
                "-- Against: null@offline:postgresql?changeLogFile=/tmp/corda/tmp/offline-db/changelog-a0d1eff0-d055-437b-99ad-82601c4362a5.xml&outputLiquibaseSql=all\n" +
                "-- Liquibase version: 4.24.0\n" +
                "-- *********************************************************************\n" +
                "\n" +
                "-- Changeset net/corda/db/schema/vnode-vault/migration/vnode-vault-creation-v1.0.xml::vnode-vault-creation-v1.0::R3.Corda\n" +
                "CREATE TABLE vnode_vault (holding_identity_id VARCHAR(12) NOT NULL);\n"
        app.get("api/v5_3/virtualnode/create/db/vault/123") { ctx ->
            ctx.header("Content-Type", "application/json")
            ctx.result(sqlResponse)
        }

        val client = CordaRestClient.createHttpClient(baseUrl = localhost)
        assertThatCodeDoesNotThrowAnyException {
            val response: String = client.virtualNodeClient.getVirtualnodeCreateDbVaultCpichecksum("123")
            assertThat(response).contains("-- Update Database Script\n")
        }
    }

    /**
     * See comment for workaround details
     * https://r3-cev.atlassian.net/browse/ES-2378?focusedCommentId=306841
     */
    @Test
    fun testHelloEndpointHandlesString() {
        val username = "TestUsername"
        val greeting = "\"Hello, $username! (from admin)\""
        app.post("api/v5_3/hello") { ctx ->
            ctx.header("Content-Type", "application/json")
            ctx.result(greeting)
        }

        val client = CordaRestClient.createHttpClient(baseUrl = localhost)
        assertThatCodeDoesNotThrowAnyException {
            val response: String = client.helloRestClient.postHello(username)
            assertThat(response).isEqualTo(greeting)
        }
    }

    /**
     * The generated client can't deserialize the Instant type natively
     * The suggested fix is to add the dependency on libs.jackson.datatype.jsr310
     * After adding the dependency this now works locally, but still fails on Jenkins
    */
    @Test
    fun testGetRole() {
        val response = """
            [{"id":"62badb8b-1836-4a89-901a-fd6b65906a67","version":0,"updateTimestamp":"2024-05-17T08:33:07.602Z","roleName":"Default System Admin","groupVisibility":null,"permissions":[{"id":"4b3477d9-b812-4ae5-bcb1-5537c7a373d5","createdTimestamp":"2024-05-17T08:33:07.692Z"}]}]
        """.trimIndent()
        app.get("api/v5_3/role") { ctx ->
            ctx.header("Content-Type", "application/json")
            ctx.result(response)
        }
        val client = CordaRestClient.createHttpClient(baseUrl = localhost)
        val roles = client.rbacRoleClient.getRole()
        assertThat(roles).isNotEmpty
        assertThat(roles.first().updateTimestamp).isNotNull
    }
}
