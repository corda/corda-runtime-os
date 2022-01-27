package net.corda.httprpc.server.impl.security.provider.bearer.oauth

import com.nimbusds.jwt.JWTParser
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import javax.security.auth.login.FailedLoginException

class PriorityListJwtClaimExtractorTest {
    private val token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6InVz" +
            "ZXJAdGVzdC5jb20iLCJpYXQiOjE1MTYyMzkwMjIsImV4cCI6MTUxNjIzOTAyMiwib2lkIjoiaWQifQ.dWUlvy4GCaLDIzuWzgsp7VLMaKUYOiQbgt-UbKcKc_s"
    private val username = "user@test.com"

    @Test
    fun `getUsername_missingPrincipalClaim_shouldThrow`() {
        val extractor = PriorityListJwtClaimExtractor(listOf("random"))
        val jwt = JWTParser.parse(token)

        Assertions.assertThrows(FailedLoginException::class.java) {
            extractor.getUsername(jwt.jwtClaimsSet)
        }
    }

    @Test
    fun `getUsername_username_shouldBeExtracted`() {
        val extractor = PriorityListJwtClaimExtractor(listOf("random", "name"))
        val jwt = JWTParser.parse(token)

        assertEquals(username, extractor.getUsername(jwt.jwtClaimsSet))
    }
}
