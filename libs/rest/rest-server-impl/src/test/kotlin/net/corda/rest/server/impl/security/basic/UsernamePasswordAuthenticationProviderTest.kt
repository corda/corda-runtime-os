package net.corda.rest.server.impl.security.basic

import net.corda.rest.server.impl.security.provider.basic.UsernamePasswordAuthenticationProvider
import net.corda.rest.server.impl.security.provider.scheme.AuthenticationSchemeProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class UsernamePasswordAuthenticationProviderTest {
    @Test
    fun `Ensure that the REALM_KEY is set to the correct value in the UsernamePasswordAuthenticationProvider`() {
        assertEquals(
            UsernamePasswordAuthenticationProvider.REALM_VALUE,
            UsernamePasswordAuthenticationProvider(mock()).provideParameters()[AuthenticationSchemeProvider.REALM_KEY]
        )
    }
}
