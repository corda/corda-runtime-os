package net.corda.restclient.generated

import io.javalin.Javalin
import net.corda.restclient.CordaRestClient
import net.corda.restclient.generated.apis.CertificateApi
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File

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
            app = Javalin.create().start(8888)
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            app.stop()
        }
    }

    /**
     * See comment for workaround details
     * https://r3-cev.atlassian.net/browse/ES-2162?focusedCommentId=302712
     */
    @Test
    fun testPostHelloCanHandleRawStringResponse(){
        app.post("api/v5_2/hello") { ctx ->
            val name = ctx.queryParam("addressee") ?: "Guest"
            ctx.header("Content-Type", "application/json")
            ctx.result("Hello, $name! (from admin)")

        }
        val client = CordaRestClient.createHttpClient(baseUrl = "http:localhost:8888")

        assertThatCode {
            val response: String = client.helloRestClient.postHello("Foo")
            assertThat(response).contains("Foo")
        }
        .withFailMessage("Has the generated api been re-generated? Re-apply workaround")
        .doesNotThrowAnyException()
    }

    /**
     * See comment for workaround details
     * https://r3-cev.atlassian.net/browse/ES-2162?focusedCommentId=302706
     */
    @Test
    fun testGetGroupPolicyFromMgm() {
        val inputStream = File("./src/test/resources/groupPolicy.json").inputStream()
        app.get("api/v5_2/mgm/1234/info") {ctx ->
            ctx.header("Content-Type", "application/json")
            ctx.result(inputStream)
        }
        val client = CordaRestClient.createHttpClient(baseUrl = "http:localhost:8888")

        assertThatCode {
            val response: String = client.mgmClient.getMgmHoldingidentityshorthashInfo("1234")
            assertThat(response).contains("groupId")
        }
        .withFailMessage("Has the generated api been re-generated? Re-apply workaround")
        .doesNotThrowAnyException()
    }

    /**
     * See comment for workaround details
     * https://r3-cev.atlassian.net/browse/ES-2162?focusedCommentId=303584
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
     * See comment for workaround details
     * https://r3-cev.atlassian.net/browse/ES-2162?focusedCommentId=303584
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

}