package net.corda.rest.server.impl.security.basic

import io.javalin.http.ForbiddenResponse
import net.corda.rest.security.AuthorizingSubject
import net.corda.rest.server.impl.security.provider.AuthenticationProvider
import net.corda.rest.server.impl.security.provider.credentials.tokens.BearerTokenAuthenticationCredentials
import net.corda.rest.server.impl.security.provider.credentials.tokens.UsernamePasswordAuthenticationCredentials
import net.corda.rest.server.impl.security.provider.scheme.AuthenticationSchemeProvider
import net.corda.rest.RestResource
import net.corda.rest.security.read.RestSecurityManager
import net.corda.rest.server.impl.context.ContextUtils
import net.corda.rest.server.impl.security.provider.basic.UsernamePasswordAuthenticationProvider
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import javax.security.auth.login.FailedLoginException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertDoesNotThrow
import java.lang.reflect.Method
import java.util.function.Supplier

class UsernamePasswordAuthenticationProviderTest {
    @Test
    fun `Ensure that the REALM_KEY is set to the correct value in the UsernamePasswordAuthenticationProvider`() {
        assertEquals("Corda REST Worker", UsernamePasswordAuthenticationProvider(mock()).provideParameters()[AuthenticationSchemeProvider.REALM_KEY])
    }
}