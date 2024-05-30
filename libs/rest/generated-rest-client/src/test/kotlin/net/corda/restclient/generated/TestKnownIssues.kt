package net.corda.restclient.generated

import io.javalin.Javalin
import net.corda.restclient.CordaRestClient
import net.corda.restclient.dto.GenerateCsrWrapperRequest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
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
    }

    private val localhost = URI.create("http:localhost:${app.port()}")

    /**
     * See comment for workaround details
     * https://r3-cev.atlassian.net/browse/ES-2162?focusedCommentId=302706
     */
    @Test
    fun testGetGroupPolicyFromMgm() {
        val inputStream = File("./src/test/resources/groupPolicy.json").inputStream()
        app.get("api/v5_3/mgm/1234/info") {ctx ->
            ctx.header("Content-Type", "application/json")
            ctx.result(inputStream)
        }
        val client = CordaRestClient.createHttpClient(baseUrl = localhost)

        assertThatCode {
            val response: String = client.mgmClient.getMgmHoldingidentityshorthashInfo("1234")
            assertThat(response).contains("groupId")
        }
        .withFailMessage("Has the generated api been re-generated? Re-apply workaround")
        .doesNotThrowAnyException()
    }

    /**
     * See comment for workaround details
     * https://r3-cev.atlassian.net/browse/ES-2162?focusedCommentId=304184
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
        app.post("api/v5_3/certificate/p2p/123") {ctx ->
            ctx.header("Content-Type", "application/json")
            ctx.result(csrResponse)
        }

        val client = CordaRestClient.createHttpClient(baseUrl = localhost)
        val requestBody = GenerateCsrWrapperRequest(
            x500Name = "OU=[localhost], O=P2P Certificate, L=London, C=GB",
            contextMap = null,
            subjectAlternativeNames = listOf("localhost")
        )

        assertThatCode {
            val response: String = client.certificatesClient.postCertificateTenantidKeyid("p2p","123", requestBody)
            assertThat(response).contains("BEGIN CERTIFICATE REQUEST")
        }
            .withFailMessage("Has the generated api been re-generated? Re-apply workaround")
            .doesNotThrowAnyException()
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
        app.get("api/v5_3/role") {ctx ->
            ctx.header("Content-Type", "application/json")
            ctx.result(response)
        }
        val client = CordaRestClient.createHttpClient(baseUrl = localhost)
        val roles = client.rbacRoleClient.getRole()
        assertThat(roles).isNotEmpty
        assertThat(roles.first().updateTimestamp).isNotNull
    }

}