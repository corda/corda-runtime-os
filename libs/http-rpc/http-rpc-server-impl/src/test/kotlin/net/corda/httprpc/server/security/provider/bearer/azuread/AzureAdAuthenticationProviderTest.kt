package net.corda.httprpc.server.security.provider.bearer.azuread

import com.nimbusds.jwt.JWTParser
import net.corda.httprpc.security.read.AdminSubject
import net.corda.httprpc.security.read.RPCSecurityManager
import net.corda.httprpc.server.config.AzureAdSettingsProvider
import net.corda.httprpc.server.security.provider.bearer.oauth.JwtProcessor
import net.corda.httprpc.server.security.provider.credentials.tokens.BearerTokenAuthenticationCredentials
import net.corda.httprpc.server.security.provider.scheme.AuthenticationSchemeProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AzureAdAuthenticationProviderTest {

    private val token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6InVz" +
            "ZXJAdGVzdC5jb20iLCJpYXQiOjE1MTYyMzkwMjIsImV4cCI6MTUxNjIzOTAyMiwib2lkIjoiaWQifQ.dWUlvy4GCaLDIzuWzgsp7VLMaKUYOiQbgt-UbKcKc_s"
    private val username = "user@test.com"
    private val authority = "authority"

    private val settings: AzureAdSettingsProvider = mock()
    private val jwtProcessor: JwtProcessor = mock()
    private val rpcSecurityManager = mock<RPCSecurityManager>().apply {
        whenever(buildSubject(any())).thenReturn(AdminSubject(username))
    }

    private lateinit var provider: AzureAdAuthenticationProvider

    @BeforeEach
    fun setUp() {
        whenever(settings.getAuthority()).thenReturn(authority)
        whenever(settings.getPrincipalClaimList()).thenReturn(listOf("random", "name"))

        provider = AzureAdAuthenticationProvider(settings, jwtProcessor, rpcSecurityManager)
    }

    @Test
    fun `provideParameters_shouldHaveAuthorityAsRealm`() {
        val params = provider.provideParameters()
        assertEquals(authority, params[AuthenticationSchemeProvider.REALM_KEY])
    }

    @Test
    fun `provideParameters_shouldHaveScope`() {
        val params = provider.provideParameters()
        assertEquals(AzureAdAuthenticationProvider.SCOPE, params[AzureAdAuthenticationProvider.SCOPE_KEY])
    }

    @Test
    fun `authenticate_username_shouldBeExtracted`() {
        val jwt = JWTParser.parse(token)
        whenever(jwtProcessor.process(argThat { serialize() == token })).thenReturn(jwt.jwtClaimsSet)

        val subject = provider.authenticate(BearerTokenAuthenticationCredentials(token))

        assertEquals(username, subject.principal)
    }
}
