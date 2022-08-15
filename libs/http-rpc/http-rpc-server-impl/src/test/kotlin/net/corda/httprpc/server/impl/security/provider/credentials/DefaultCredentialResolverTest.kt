package net.corda.httprpc.server.impl.security.provider.credentials

import io.javalin.core.util.Header.AUTHORIZATION
import io.javalin.http.Context
import net.corda.httprpc.server.impl.context.ClientHttpRequestContext
import net.corda.httprpc.server.impl.security.provider.credentials.tokens.BearerTokenAuthenticationCredentials
import net.corda.httprpc.server.impl.security.provider.credentials.tokens.UsernamePasswordAuthenticationCredentials
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Base64
import javax.security.auth.login.FailedLoginException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class DefaultCredentialResolverTest {
    //need to mock these as Javalin's context class in unmockable due to overload resolution issues
    //https://github.com/mockito/mockito/issues/1943
    private val req: HttpServletRequest = mock()
    private val res: HttpServletResponse = mock()
    private val context = ClientHttpRequestContext(Context(req, res, emptyMap()))
    private val resolver = DefaultCredentialResolver()

    @Test
    fun `resolve empty credential should return empty credential when no other available`() {
        assertNull(resolver.resolve(context))
    }

    @Test
    fun `resolve bearer token should return bearer token credential`() {
        val token = "token"
        whenever(req.getHeader(AUTHORIZATION)).thenReturn("Bearer $token")

        assertEquals(BearerTokenAuthenticationCredentials(token), resolver.resolve(context))
    }

    @Test
    fun `resolve malformed BearerToken should throw`() {
        whenever(req.getHeader(AUTHORIZATION)).thenReturn("Bearer_token")

        Assertions.assertThrows(FailedLoginException::class.java) {
            resolver.resolve(context)
        }
    }

    @Test
    fun `resolve basic auth credential should return basic authentication credential`() {
        val username = "user"
        val password = "pass"

        whenever(req.getHeader(AUTHORIZATION)).thenReturn(
            "Basic ${
                Base64.getEncoder().encodeToString("$username:$password".toByteArray())
            }"
        )

        assertEquals(UsernamePasswordAuthenticationCredentials(username, password), resolver.resolve(context))
    }
}
