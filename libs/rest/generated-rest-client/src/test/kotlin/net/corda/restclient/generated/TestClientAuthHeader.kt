package net.corda.restclient.generated

import io.javalin.Javalin
import io.javalin.http.ContentType
import io.javalin.http.Header
import io.javalin.http.servlet.getBasicAuthCredentials
import net.corda.restclient.CordaRestClient
import net.corda.restclient.generated.infrastructure.ApiClient
import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
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

                val basicAuthCredentials = getBasicAuthCredentials(ctx.header(Header.AUTHORIZATION))
                assertThat(basicAuthCredentials).isNotNull()

                // Respond with the credentials from the basic auth header
                ctx.contentType(ContentType.APPLICATION_JSON)
                    .result("${basicAuthCredentials?.username}:${basicAuthCredentials?.password}")
            }
        }

        @AfterAll
        @JvmStatic
        fun teardown() { app.stop() }
    }

    private val localhost = URI.create("http:localhost:${app.port()}")

    private fun CordaRestClient.echoCredentials(): String {
        return this.mgmClient.getMgmHoldingidentityshorthashInfo("1234")
    }

    @BeforeEach
    fun resetStaticCredentials() {
        ApiClient.username = null
        ApiClient.password = null
    }

    private fun assertThatStaticCredsAreNull() {
        assertThat(ApiClient.username).isNull()
        assertThat(ApiClient.password).isNull()
    }

    @Test
    fun defaultCredentialsAreCorrect() {
        val defaultClient = CordaRestClient.createHttpClient(localhost)
        assertThat(defaultClient.echoCredentials()).isEqualTo("admin:admin")

        assertThatStaticCredsAreNull()
    }

    @Test
    fun staticCredentialsAreIgnored() {
        val defaultClient = CordaRestClient.createHttpClient(localhost, "foo", "bar")
        assertThat(defaultClient.echoCredentials()).isEqualTo("foo:bar")

        assertThatStaticCredsAreNull()

        ApiClient.apply {
            username = "foo-static"
            password = "bar-static"
        }
        assertThat(defaultClient.echoCredentials()).isEqualTo("foo:bar")

        assertThat(ApiClient.username).isEqualTo("foo-static")
        assertThat(ApiClient.password).isEqualTo("bar-static")
    }

    @Test
    fun twoClientsUseUniqueCredentials() {
        val client1 = CordaRestClient.createHttpClient(localhost, "user-one", "password-one")
        val client2 = CordaRestClient.createHttpClient(localhost, "user-two", "password-two")
        ApiClient.apply {
            username = "user-static"
            password = "password-static"
        }
        assertThat(client1.echoCredentials()).isEqualTo("user-one:password-one")
        assertThat(client2.echoCredentials()).isEqualTo("user-two:password-two")

        resetStaticCredentials()

        assertThat(client1.echoCredentials()).isEqualTo("user-one:password-one")
        assertThat(client2.echoCredentials()).isEqualTo("user-two:password-two")
    }
}
