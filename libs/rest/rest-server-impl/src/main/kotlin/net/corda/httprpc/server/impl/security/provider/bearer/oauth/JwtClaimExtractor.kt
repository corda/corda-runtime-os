package net.corda.httprpc.server.impl.security.provider.bearer.oauth

import com.nimbusds.jwt.JWTClaimsSet

internal interface JwtClaimExtractor {
    fun getUsername(claimsSet: JWTClaimsSet): String
}
