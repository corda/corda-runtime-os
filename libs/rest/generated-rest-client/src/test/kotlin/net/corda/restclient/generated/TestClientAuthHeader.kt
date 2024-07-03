package net.corda.restclient.generated

import io.javalin.Javalin
import io.javalin.http.ContentType
import net.corda.restclient.CordaRestClient
import net.corda.restclient.generated.infrastructure.ApiClient
import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.net.URI

class TestClientAuthHeader {

    companion object {
        private lateinit var app: Javalin

        @BeforeAll
        @JvmStatic
        fun setup() {
            app = Javalin.create().start(0)
            app.get("api/v5_3/mgm/1234/info") { ctx ->
                val (user, password) = ctx.basicAuthCredentials()
                ctx.contentType(ContentType.APPLICATION_JSON)
                    .status(200)
                    .result("$user:$password")
            }
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            app.stop()
        }

    }

    private val localhost = URI.create("http:localhost:${app.port()}")

    private fun CordaRestClient.echoCredentials(): String {
        return this.mgmClient.getMgmHoldingidentityshorthashInfo("1234")
    }

    @Test
    fun defaultCredentialsAreCorrect() {
        val defaultClient = CordaRestClient.createHttpClient(localhost)
        assertThat(defaultClient.echoCredentials()).isEqualTo("admin:admin")
    }

    @Test
    fun staticCredentialsAreIgnored() {
        val defaultClient = CordaRestClient.createHttpClient(localhost, "foo", "bar")
        assertThat(defaultClient.echoCredentials()).isEqualTo("foo:bar")
        ApiClient.apply {
            username = "foo-static"
            password = "bar-static"
        }
        assertThat(defaultClient.echoCredentials()).isNotEqualTo("foo-static:bar-static")
        assertThat(defaultClient.echoCredentials()).isNotEmpty()
        assertThat(defaultClient.echoCredentials()).isEqualTo("foo:bar")
    }

    @Test
    fun twoClientsUseUniqueCredentials() {
        val client1 = CordaRestClient.createHttpClient(localhost, "user-one", "password-one")
        val client2 = CordaRestClient.createHttpClient(localhost, "user-two", "password-two")
        ApiClient.apply {
            username = "user-static"
            password = "password-static"
        }
        assertThat(client1.echoCredentials()).isNotEqualTo("user-static:password-static")
        assertThat(client1.echoCredentials()).isEqualTo("user-one:password-one")

        assertThat(client2.echoCredentials()).isNotEqualTo("user-static:password-static")
        assertThat(client2.echoCredentials()).isEqualTo("user-two:password-two")
    }
}
