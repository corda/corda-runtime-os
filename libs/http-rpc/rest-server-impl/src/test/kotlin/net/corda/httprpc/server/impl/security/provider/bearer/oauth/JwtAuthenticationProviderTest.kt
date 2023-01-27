package net.corda.httprpc.server.impl.security.provider.bearer.oauth

import com.nimbusds.jose.proc.BadJOSEException
import com.nimbusds.jwt.JWTParser
import net.corda.httprpc.security.read.RPCSecurityManager
import net.corda.httprpc.server.impl.security.TestRestResource
import net.corda.httprpc.server.impl.security.provider.bearer.TestAdminSubject
import net.corda.httprpc.server.impl.security.provider.credentials.tokens.BearerTokenAuthenticationCredentials
import net.corda.httprpc.server.impl.security.provider.credentials.tokens.UsernamePasswordAuthenticationCredentials
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import javax.security.auth.login.FailedLoginException

class JwtAuthenticationProviderTest {

    private val token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6InVz" +
            "ZXJAdGVzdC5jb20iLCJpYXQiOjE1MTYyMzkwMjIsImV4cCI6MTUxNjIzOTAyMiwib2lkIjoiaWQifQ.dWUlvy4GCaLDIzuWzgsp7VLMaKUYOiQbgt-UbKcKc_s"
    private val username = "user@test.com"
    private val jwtProcessor: JwtProcessor = mock()
    private val claimExtractor: JwtClaimExtractor = mock()
    private val permission = "InvokeRpc:${TestRestResource::class.java.name}#dummy2"

    private val rpcSecurityManager = mock<RPCSecurityManager>().apply {
        whenever(buildSubject(any())).thenReturn(TestAdminSubject(username))
    }

    private val provider = JwtAuthenticationProvider(jwtProcessor, claimExtractor, ::rpcSecurityManager)

    @Test
    fun `supports_basicAuthenticationCredential_shouldReturnFalse`() {
        assertFalse(provider.supports(UsernamePasswordAuthenticationCredentials("", "")))
    }

    @Test
    fun `supports_bearerTokenCredential_shouldReturnTrue`() {
        assert(provider.supports(BearerTokenAuthenticationCredentials("")))
    }

    @Test
    fun `authenticate_invalidCredential_shouldThrow`() {
        Assertions.assertThrows(IllegalArgumentException::class.java) {
            provider.authenticate(UsernamePasswordAuthenticationCredentials("testUser", "testPassword"))
        }
    }

    @Test
    fun `authenticate_malformedJwt_shouldThrow`() {
        Assertions.assertThrows(FailedLoginException::class.java) {
            provider.authenticate(BearerTokenAuthenticationCredentials("random"))
        }
    }

    @Test
    fun `authenticate_invalidJwt_shouldThrow`() {
        whenever(jwtProcessor.process(argThat { serialize() == token })).thenAnswer { throw BadJOSEException("") }

        Assertions.assertThrows(FailedLoginException::class.java) {
            provider.authenticate(BearerTokenAuthenticationCredentials(token))
        }
    }

    @Test
    fun `authenticate_username_shouldBeExtracted`() {
        val jwt = JWTParser.parse(token)
        whenever(jwtProcessor.process(argThat { serialize() == token })).thenReturn(jwt.jwtClaimsSet)
        whenever(claimExtractor.getUsername(jwt.jwtClaimsSet)).thenReturn(username)

        val subject = provider.authenticate(BearerTokenAuthenticationCredentials(token))

        assert(subject.isPermitted(permission.replace("InvokeRpc:", "")))
    }
}
