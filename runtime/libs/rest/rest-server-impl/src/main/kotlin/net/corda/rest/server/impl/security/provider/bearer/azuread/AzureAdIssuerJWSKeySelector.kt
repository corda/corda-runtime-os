package net.corda.rest.server.impl.security.provider.bearer.azuread

import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.BadJOSEException
import com.nimbusds.jose.proc.JWSAlgorithmFamilyJWSKeySelector
import com.nimbusds.jose.proc.JWSKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jose.util.ResourceRetriever
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.JWTClaimsSetAwareJWSKeySelector
import org.slf4j.LoggerFactory
import java.security.Key
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal class AzureAdIssuerJWSKeySelector(
    private val azureAdIssuers: AzureAdIssuers,
    private val resourceRetriever: ResourceRetriever
) : JWTClaimsSetAwareJWSKeySelector<SecurityContext> {
    companion object {
        private const val ISS_CLAIM = "iss"
        private val DEFAULT_JWK_CACHE_REFRESH = TimeUnit.MINUTES.toMillis(4L)
        private val DEFAULT_JWK_CACHE_TTL = TimeUnit.MINUTES.toMillis(5L)

        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val keySelectors = ConcurrentHashMap<String, JWSKeySelector<SecurityContext>>()

    override fun selectKeys(
        header: JWSHeader?,
        claimsSet: JWTClaimsSet?,
        context: SecurityContext?
    ): MutableList<out Key> {
        val iss = claimsSet?.getStringClaim(ISS_CLAIM)
        if (iss == null || !azureAdIssuers.valid(iss)) {
            throw BadJOSEException("The issuer $iss is unknown.")
        }

        return keySelectors.computeIfAbsent(iss) { issuer ->
            try {
                val config = AzureAdConfiguration.fromIssuer(issuer, resourceRetriever)
                val jwksUri = config.jwksUri
                val keySource = JWKSourceBuilder.create<SecurityContext>(jwksUri.toURL(), resourceRetriever)
                    .cache(DEFAULT_JWK_CACHE_TTL, DEFAULT_JWK_CACHE_REFRESH)
                    .build()

                JWSAlgorithmFamilyJWSKeySelector.fromJWKSource(keySource)
            } catch (e: Exception) {
                logger.error("Unexpected error creating JWSKeySelector", e)
                throw BadJOSEException("Failed to create key source for issuer $issuer", e)
            }
        }.selectJWSKeys(header, context)
    }
}
