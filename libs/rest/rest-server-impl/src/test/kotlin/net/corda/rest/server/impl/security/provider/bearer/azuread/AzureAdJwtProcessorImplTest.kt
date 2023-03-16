package net.corda.rest.server.impl.security.provider.bearer.azuread

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.nimbusds.jwt.proc.BadJWTException
import com.nimbusds.jwt.proc.JWTClaimsSetAwareJWSKeySelector
import net.corda.rest.server.config.AzureAdSettingsProvider
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.Key

class AzureAdJwtProcessorImplTest {
    private val keyId = "key"
    private val keySet = JWKSet(
        RSAKeyGenerator(2048)
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .keyID(keyId)
            .generate()
    )
    private val signer = RSASSASigner(keySet.keys.first().toRSAKey())
    private val settings: AzureAdSettingsProvider = mock()
    private val tokenIssuers: AzureAdIssuers = mock()
    private val jwsKeySelector = JWTClaimsSetAwareJWSKeySelector<SecurityContext> { _, _, _ ->
        keySet.keys.map { it.toRSAKey().toRSAPublicKey() as Key }.toMutableList()
    }
    private val issuer = "issuer"
    private val clientId = "client-id"
    private lateinit var processor: AzureAdJwtProcessorImpl

    @BeforeEach
    fun setUp() {
        whenever(settings.getClientId()).thenReturn(clientId)
        whenever(tokenIssuers.valid(issuer)).thenReturn(true)

        processor = AzureAdJwtProcessorImpl(settings, tokenIssuers, jwsKeySelector)
    }

    @Test
    fun `process_invalidAudience_shouldThrow`() {
        val jwt = SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.RS256).build(), JWTClaimsSet.Builder().audience("random")
                .build()
        ).apply { sign(signer) }

        Assertions.assertThrows(BadJWTException::class.java) {
            processor.process(jwt)
        }
    }

    @Test
    fun `process_invalidIssuer_shouldThrow`() {
        val jwt = SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.RS256).build(), JWTClaimsSet.Builder().audience(clientId).issuer("random")
                .build()
        ).apply { sign(signer) }

        Assertions.assertThrows(BadJWTException::class.java) {
            processor.process(jwt)
        }
    }
}
