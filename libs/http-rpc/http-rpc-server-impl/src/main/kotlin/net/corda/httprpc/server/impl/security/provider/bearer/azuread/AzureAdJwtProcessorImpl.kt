package net.corda.httprpc.server.impl.security.provider.bearer.azuread

import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWT
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.BadJWTException
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import com.nimbusds.jwt.proc.JWTClaimsSetAwareJWSKeySelector
import net.corda.httprpc.server.config.AzureAdSettingsProvider
import net.corda.httprpc.server.impl.security.provider.bearer.oauth.JwtProcessor

internal class AzureAdJwtProcessorImpl(
    private val settings: AzureAdSettingsProvider,
    private val tokenIssuers: AzureAdIssuers,
    private val jwsKeySelector: JWTClaimsSetAwareJWSKeySelector<SecurityContext>
) : JwtProcessor {
    private val jwtProcessor = DefaultJWTProcessor<SecurityContext>().apply {
        jwtClaimsSetVerifier =
            object : DefaultJWTClaimsVerifier<SecurityContext>(getValidAudiences(), null, null, null) {
                override fun verify(claimsSet: JWTClaimsSet, ctx: SecurityContext?) {
                    super.verify(claimsSet, ctx)
                    val issuer: String = claimsSet.issuer
                    if (!tokenIssuers.valid(issuer)) {
                        throw BadJWTException("Invalid token issuer")
                    }
                }
            }
        jwtClaimsSetAwareJWSKeySelector = this@AzureAdJwtProcessorImpl.jwsKeySelector
    }

    override fun process(jwt: JWT): JWTClaimsSet {
        return jwtProcessor.process(jwt, null)
    }

    private fun getValidAudiences(): Set<String> {
        val result = mutableSetOf(settings.getClientId())
        val appIdUri = settings.getAppIdUri()
        if (appIdUri != null) {
            result.add(appIdUri)
        }
        return result
    }
}
