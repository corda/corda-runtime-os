package net.corda.rest.server.impl.security.basic

import net.corda.rest.server.impl.security.provider.scheme.AuthenticationSchemeProvider
import net.corda.rest.server.impl.security.provider.basic.UsernamePasswordAuthenticationProvider
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.junit.jupiter.api.Assertions.assertEquals

class UsernamePasswordAuthenticationProviderTest {
    @Test
    fun `Ensure that the REALM_KEY is set to the correct value in the UsernamePasswordAuthenticationProvider`() {
        assertEquals("Corda REST Worker", UsernamePasswordAuthenticationProvider(mock()).provideParameters()[AuthenticationSchemeProvider.REALM_KEY])
    }
}