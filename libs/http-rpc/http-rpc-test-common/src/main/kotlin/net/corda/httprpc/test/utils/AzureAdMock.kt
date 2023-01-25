package net.corda.httprpc.test.utils

import com.fasterxml.jackson.databind.ObjectMapper
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

object AzureAdMock {

    private val log = LoggerFactory.getLogger(AzureAdMock::class.java)

    const val username = "user@test.com"
    const val clientId = "client"
    const val tenantId = "tenant"
    const val issuer = "${TestURLStreamHandlerFactory.PROTOCOL}://login.microsoftonline.com/${tenantId}/v2.0"
    private const val jwksUrl = "${issuer}/jwks"
    private const val oidcMetadataUrl = "${issuer}/.well-known/openid-configuration"
    private const val oidcMetadata = "{\"jwks_uri\": \"${jwksUrl}\", \"issuer\": \"${issuer}\", \"subject_types_supported\":[\"pairwise\"]}"
    private const val keyId = "key"
    private val keySet = JWKSet(
        RSAKeyGenerator(2048)
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .keyID(keyId)
            .generate()
    )
    private val signer = RSASSASigner(keySet.keys.first().toRSAKey())

    private val keySetJson = ObjectMapper().writeValueAsString(keySet.toPublicJWKSet().toJSONObject())

    fun generateUserToken(): String {
        val jwt = SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keyId).build(), JWTClaimsSet.Builder().audience(clientId)
                .issuer(issuer)
                .expirationTime(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .claim("preferred_username", username)
                .build()
        ).apply { sign(signer) }

        return jwt.serialize()
    }

    fun generateApplicationToken(): String {
        val jwt = SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keyId).build(), JWTClaimsSet.Builder().audience(clientId)
                .issuer(issuer)
                .expirationTime(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .claim("appid", clientId)
                .build()
        ).apply { sign(signer) }

        return jwt.serialize()
    }

    fun create(): Closeable {
        log.info("Stream handler create start")
        val streamHandler = TestURLStreamHandlerFactory(
            mapOf(
                oidcMetadataUrl to oidcMetadata,
                jwksUrl to keySetJson
            )
        )
        log.info("Stream handler create end")
        streamHandler.register()
        log.info("Stream handler registered")
        return streamHandler
    }
}
